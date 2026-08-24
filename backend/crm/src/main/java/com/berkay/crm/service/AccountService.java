package com.berkay.crm.service;

import com.berkay.crm.dto.AccountCreateRequest;
import com.berkay.crm.dto.AccountResponse;
import com.berkay.crm.dto.AccountUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.*;
import com.berkay.crm.security.Roles;
import com.berkay.crm.repository.specification.AccountSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    private final ContactRepository contactRepository;

    private final ActivityRepository activityRepository;

    private final DealRepository dealRepository;

    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          ContactRepository contactRepository, ActivityRepository activityRepository,
                          DealRepository dealRepository) {

        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.activityRepository = activityRepository;
        this.dealRepository = dealRepository;
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request, CrmUser currentUser) {

        Account account = new Account();
        account.setName(request.name());
        account.setIndustry(request.industry());
        account.setWebsite(request.website());
        account.setPhone(request.phone());
        account.setOwner(resolveOwner(request.ownerId(), currentUser));

        return AccountResponse.from(accountRepository.save(account), 0);
    }

    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);

        if (!Objects.equals(account.getVersion(), request.version())) {
            throw new ConflictException(
                    "This account changed since you opened it — reload and try again");
        }

        account.setName(request.name());
        account.setIndustry(request.industry());
        account.setWebsite(request.website());
        account.setPhone(request.phone());
        account.setOwner(resolveOwner(request.ownerId(), currentUser));

        return AccountResponse.from(account, contactRepository.countByAccountId(id)); // managed
    }

    @Transactional
    public void delete(Long id, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);
        cascadeChildren(account);
        accountRepository.delete(account); // @SQLDelete turns this into a soft delete
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(Pageable pageable, CrmUser currentUser,
                                         String name, String industry, Long ownerId) {

        Specification<Account> spec =
                AccountSpecifications.forFilters(currentUser, name, industry, ownerId);

        Page<Account> accounts = accountRepository.findAll(spec, pageable);

        List<Long> ids = accounts.getContent().stream().map(Account::getId).toList();

        // `in :ids` with an empty collection is invalid SQL on some databases
        if(ids.isEmpty()) {
            return accounts.map(account -> AccountResponse.from(account, 0));
        }

        // one extra query for the entire page
        Map<Long, Long> counts = contactRepository.countByAccountIdIn(ids).stream()
                .collect(Collectors.toMap(
                        ContactRepository.AccountContactCount::getAccountId,
                        ContactRepository.AccountContactCount::getTotal
                ));

        return accounts.map(account -> AccountResponse.from(account, counts.getOrDefault(account.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);
        return AccountResponse.from(account, contactRepository.countByAccountId(id));
    }

    // Load or 404, THEN Authorize or 403 - never the opposite!
    public Account loadAccessible(Long id, CrmUser currentUser) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));

        boolean isOwner = account.getOwner().getId().equals(currentUser.getId());

        if (!isOwner && !Roles.isPrivileged(currentUser)) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        return account;
    }

    // null ownerId means "me". Assigning someone else is a privileged action.
    private CrmUser resolveOwner(Long ownerId, CrmUser currentUser) {

        Long targetId = (ownerId == null) ? currentUser.getId() : ownerId;

        if (!targetId.equals(currentUser.getId()) && !Roles.isPrivileged(currentUser)) {
            throw new AccessDeniedException("Only managers and admins can assign a different owner");
        }

        // always re-load: the principal's CrmUser is DETACHED (loaded at login)
        return userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found: " + targetId));
    }

    /**
     * @SQLDelete does not cascade, so every child type is soft-deleted explicitly.
     * Anything new hanging off an account must be added here or it becomes an orphan:
     * a live row whose only route into the app is through a hidden parent.
     */
    private void cascadeChildren(Account account) {

        account.getContacts().forEach(contactRepository::delete);
        account.getActivities().forEach(activityRepository::delete);
        account.getDeals().forEach(dealRepository::delete);
    }
}
