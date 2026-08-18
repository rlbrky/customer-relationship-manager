package com.berkay.crm.service;

import com.berkay.crm.dto.DealCreateRequest;
import com.berkay.crm.dto.DealResponse;
import com.berkay.crm.dto.DealStageHistoryResponse;
import com.berkay.crm.dto.DealUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.repository.DealStageHistoryRepository;
import com.berkay.crm.repository.specification.DealSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DealService {

    private final DealRepository dealRepository;

    private final DealStageHistoryRepository dealStageHistoryRepository;

    private final AccountService accountService;

    public DealService(DealRepository dealRepository, DealStageHistoryRepository dealStageHistoryRepository, AccountService accountService) {

        this.dealRepository = dealRepository;
        this.dealStageHistoryRepository = dealStageHistoryRepository;
        this.accountService = accountService;
    }

    @Transactional
    public DealResponse create(Long accountId, DealCreateRequest request, CrmUser user) {

        Account account = accountService.loadAccessible(accountId, user);

        DealStage stage = request.stage() != null ? request.stage() : DealStage.PROSPECT;

        Deal deal = new Deal();
        deal.setTitle(request.title());
        deal.setValue(request.value());
        deal.setStage(stage);
        deal.setExpectedCloseDate(request.expectedCloseDate());
        deal.setAccount(account);

        Deal saved = dealRepository.save(deal);

        // must be saved first
        recordStageChange(saved, null, stage, user);

        return DealResponse.from(saved);
    }

    @Transactional
    public DealResponse update(Long id, DealUpdateRequest request, CrmUser user) {

        Deal deal = loadAccessible(id, user);

        deal.setTitle(request.title());
        deal.setValue(request.value());
        deal.setExpectedCloseDate(request.expectedCloseDate());

        return DealResponse.from(deal);
    }

    @Transactional
    public void delete(Long id, CrmUser user) {

        Deal deal = loadAccessible(id, user);
        dealRepository.delete(deal);
    }

    @Transactional
    public DealResponse changeStage(Long id, DealStage newStage, CrmUser user) {

        Deal deal = loadAccessible(id, user);

        if(deal.getOutcome() != null) {
            throw new ConflictException("A closed deal cannot change stage — reopen it first");
        }

        DealStage current = deal.getStage();
        if (current == newStage){
            return DealResponse.from(deal); // no-op: dropping a card in its own column writes no history
        }

        deal.setStage(newStage);
        recordStageChange(deal, current, newStage, user);

        return DealResponse.from(deal);
    }

    @Transactional
    public DealResponse setOutcome(Long id, DealOutcome outcome, CrmUser user) {

        Deal deal = loadAccessible(id, user);

        deal.setOutcome(outcome);
        // null outcome means reopened - an open deal must not have a closed date
        deal.setClosedAt(outcome == null ? null : Instant.now());

        return DealResponse.from(deal);
    }

    @Transactional(readOnly = true)
    public Page<DealResponse> findByAccount(Long accountId, Pageable pageable, CrmUser user) {

        accountService.loadAccessible(accountId, user); // auth guard
        return dealRepository.findAll(DealSpecifications.inAccount(accountId), pageable)
                .map(DealResponse::from);
    }

    /** The pipeline board: every deal this user may see, optionally filtered. */
    @Transactional(readOnly = true)
    public Page<DealResponse> findAll(Pageable pageable, CrmUser user,
                                      DealStage stage, Boolean open) {

        Specification<Deal> spec = DealSpecifications.visibleTo(user);
        if (stage != null)
            spec = spec.and(DealSpecifications.ofStage(stage));

        if (open != null)
            spec = spec.and(DealSpecifications.isOpen(open));

        return dealRepository.findAll(spec, pageable).map(DealResponse::from);
    }

    @Transactional(readOnly = true)
    public DealResponse findById(Long id, CrmUser user) {

        return DealResponse.from(loadAccessible(id, user));
    }

    @Transactional(readOnly = true)
    public List<DealStageHistoryResponse> findHistory(Long id, CrmUser user) {

        loadAccessible(id, user); // authorization guard
        return dealStageHistoryRepository.findByDealIdOrderByChangedAtAsc(id).stream()
                .map(DealStageHistoryResponse::from)
                .toList();
    }

    private Deal loadAccessible(Long id, CrmUser user) {

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found with id: " + id));

        // authorization inherited
        accountService.loadAccessible(deal.getAccount().getId(), user);

        return deal;
    }

    private void recordStageChange(Deal deal, DealStage from, DealStage to, CrmUser user) {

        DealStageHistory history = new DealStageHistory();
        history.setDeal(deal);
        history.setFromStage(from);
        history.setToStage(to);
        history.setChangedAt(Instant.now());
        history.setChangedBy(user.getUsername());

        dealStageHistoryRepository.save(history);
    }
}
