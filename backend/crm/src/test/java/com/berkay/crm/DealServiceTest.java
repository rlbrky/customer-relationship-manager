package com.berkay.crm;

import com.berkay.crm.dto.DealCreateRequest;
import com.berkay.crm.dto.DealResponse;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.repository.DealStageHistoryRepository;
import com.berkay.crm.service.AccountService;
import com.berkay.crm.service.DealService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    @Mock DealRepository dealRepository;
    @Mock DealStageHistoryRepository dealStageHistoryRepository;
    @Mock AccountService accountService;
    @InjectMocks DealService dealService;

    private CrmUser userWith(Long id) {
        CrmUser user = new CrmUser();
        user.setId(id);
        user.setUsername("user" + id);
        Role role = new Role();
        role.setName("ROLE_SALES_REP");
        user.getRoles().add(role);
        return user;
    }

    private Account accountWith(Long id, CrmUser owner) {
        Account account = new Account();
        account.setId(id);
        account.setName("Acme");
        account.setOwner(owner);
        return account;
    }

    private Deal dealWith(Long id, Account account, DealStage stage, DealOutcome outcome) {
        Deal deal = new Deal();
        deal.setId(id);
        deal.setTitle("Renewal");
        deal.setStage(stage);
        deal.setOutcome(outcome);
        deal.setAccount(account);
        return deal;
    }

    @Test
    void create_writesInitialHistoryWithNullFromStage() {
        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user)).willReturn(accountWith(10L, user));
        given(dealRepository.save(any(Deal.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        dealService.create(10L, new DealCreateRequest(
                "Renewal", new BigDecimal("1250.00"), DealStage.QUALIFIED, LocalDate.now()), user);

        // then — the opening row of the audit trail: the deal came from nowhere
        ArgumentCaptor<DealStageHistory> captor = ArgumentCaptor.forClass(DealStageHistory.class);
        verify(dealStageHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStage()).isNull();
        assertThat(captor.getValue().getToStage()).isEqualTo(DealStage.QUALIFIED);
        assertThat(captor.getValue().getChangedBy()).isEqualTo("user1");
    }

    @Test
    void create_defaultsStageToProspectWhenAbsent() {
        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user)).willReturn(accountWith(10L, user));
        given(dealRepository.save(any(Deal.class))).willAnswer(inv -> inv.getArgument(0));

        // when — no stage supplied
        DealResponse response = dealService.create(10L,
                new DealCreateRequest("Renewal", null, null, null), user);

        // then
        assertThat(response.stage()).isEqualTo(DealStage.PROSPECT);
    }

    @Test
    void changeStage_writesHistoryRow() {
        // given
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.PROSPECT, null);
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when
        dealService.changeStage(5L, DealStage.PROPOSAL, user);

        // then
        assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);

        ArgumentCaptor<DealStageHistory> captor = ArgumentCaptor.forClass(DealStageHistory.class);
        verify(dealStageHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStage()).isEqualTo(DealStage.PROSPECT);
        assertThat(captor.getValue().getToStage()).isEqualTo(DealStage.PROPOSAL);
    }

    @Test
    void changeStage_toSameStage_writesNoHistory() {
        // given
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.PROPOSAL, null);
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when — dropping a Kanban card back into its own column
        dealService.changeStage(5L, DealStage.PROPOSAL, user);

        // then — no duplicate row polluting the audit trail
        verify(dealStageHistoryRepository, never()).save(any());
    }

    @Test
    void changeStage_onClosedDeal_throwsConflict() {
        // given — a won deal
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.NEGOTIATION, DealOutcome.WON);
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when / then
        assertThatThrownBy(() -> dealService.changeStage(5L, DealStage.PROPOSAL, user))
                .isInstanceOf(ConflictException.class);

        assertThat(deal.getStage()).isEqualTo(DealStage.NEGOTIATION); // unchanged
        verify(dealStageHistoryRepository, never()).save(any());
    }

    @Test
    void setOutcome_stampsClosedAt() {
        // given
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.NEGOTIATION, null);
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when
        dealService.setOutcome(5L, DealOutcome.WON, user);

        // then
        assertThat(deal.getOutcome()).isEqualTo(DealOutcome.WON);
        assertThat(deal.getClosedAt()).isNotNull();
        // closing is not a stage change, so nothing lands in the stage history
        verify(dealStageHistoryRepository, never()).save(any());
    }

    @Test
    void setOutcome_nullReopensAndClearsClosedAt() {
        // given — a closed deal
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.NEGOTIATION, DealOutcome.LOST);
        deal.setClosedAt(Instant.now());
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when
        dealService.setOutcome(5L, null, user);

        // then — an open deal must not claim a close date
        assertThat(deal.getOutcome()).isNull();
        assertThat(deal.getClosedAt()).isNull();
    }

    @Test
    void findByAccount_propagatesAccessDenied() {
        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user))
                .willThrow(new AccessDeniedException("denied"));

        // when / then
        assertThatThrownBy(() -> dealService.findByAccount(10L, Pageable.unpaged(), user))
                .isInstanceOf(AccessDeniedException.class);

        verify(dealRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    @Test
    void delete_callsRepositoryDeleteForSoftDelete() {
        // given
        CrmUser user = userWith(1L);
        Account account = accountWith(10L, user);
        Deal deal = dealWith(5L, account, DealStage.PROSPECT, null);
        given(dealRepository.findById(5L)).willReturn(Optional.of(deal));
        given(accountService.loadAccessible(10L, user)).willReturn(account);

        // when
        dealService.delete(5L, user);

        // then — @SQLDelete turns this into an UPDATE ... SET deleted_at
        verify(dealRepository).delete(deal);
    }
}
