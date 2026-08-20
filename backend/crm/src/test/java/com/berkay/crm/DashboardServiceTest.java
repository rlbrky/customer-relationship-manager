package com.berkay.crm;

import com.berkay.crm.dto.DashboardSummaryResponse;
import com.berkay.crm.dto.StageSummary;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.DealOutcome;
import com.berkay.crm.model.DealStage;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest {

    @Mock
    private DealRepository dealRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private DashboardService dashboardService;

    /**
     * Implementing the projection beats mocking it: five given() lines per row is
     * unreadable, and the record makes the fixture say what it means.
     */
    private record StageRow(DealStage stage, long dealCount, BigDecimal totalValue)
            implements DealRepository.StageTotal {
        @Override public DealStage getStage() { return stage; }
        @Override public long getDealCount() { return dealCount; }
        @Override public BigDecimal getTotalValue() { return totalValue; }
    }

    private record OutcomeRow(DealOutcome outcome, long dealCount, BigDecimal totalValue)
            implements DealRepository.OutcomeTotal {
        @Override public DealOutcome getOutcome() { return outcome; }
        @Override public long getDealCount() { return dealCount; }
        @Override public BigDecimal getTotalValue() { return totalValue; }
    }

    private CrmUser userWith(Long id, String roleName) {

        Role role = new Role();
        role.setName(roleName);

        CrmUser user = new CrmUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.getRoles().add(role);
        return user;
    }

    private CrmUser manager() {
        return userWith(1L, Roles.MANAGER);
    }

    private CrmUser salesRep(Long id) {
        return userWith(id, Roles.SALES_REP);
    }

    private StageSummary stageOf(DashboardSummaryResponse summary, DealStage stage) {
        return summary.pipelineByStage().stream()
                .filter(row -> row.stage() == stage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no summary for stage " + stage));
    }

    /**
     * summary() always reaches closingSoon(), and Mockito returns null — not an empty
     * page — for an unstubbed method returning Page. Every test needs this; the
     * List-returning aggregates default to empty lists on their own.
     */
    @BeforeEach
    public void stubClosingSoon() {
        given(dealRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty());
    }

    @Test
    public void summary_fillsStagesWithNoDealsAsZero() {

        // given — the query answers for two of the four stages
        given(dealRepository.pipelineByStage(any())).willReturn(List.of(
                new StageRow(DealStage.PROSPECT, 2L, new BigDecimal("350.00")),
                new StageRow(DealStage.PROPOSAL, 1L, new BigDecimal("400.00"))));

        // when
        DashboardSummaryResponse summary = dashboardService.summary(manager());

        // then — the board always has four columns, so the read model always has four rows
        assertThat(summary.pipelineByStage()).hasSize(4);
        assertThat(stageOf(summary, DealStage.QUALIFIED).dealCount()).isZero();
        assertThat(stageOf(summary, DealStage.QUALIFIED).totalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stageOf(summary, DealStage.PROSPECT).dealCount()).isEqualTo(2L);
    }

    @Test
    public void summary_ordersStagesByPipelineOrder() {

        // given — rows in whatever order the database felt like
        given(dealRepository.pipelineByStage(any())).willReturn(List.of(
                new StageRow(DealStage.NEGOTIATION, 1L, BigDecimal.TEN),
                new StageRow(DealStage.PROSPECT, 1L, BigDecimal.TEN)));

        // when
        DashboardSummaryResponse summary = dashboardService.summary(manager());

        // then — containsExactly is right here: unlike GROUP BY, the service promises
        // an order, and it is the enum's declaration order
        assertThat(summary.pipelineByStage())
                .extracting(StageSummary::stage)
                .containsExactly(DealStage.PROSPECT, DealStage.QUALIFIED,
                        DealStage.PROPOSAL, DealStage.NEGOTIATION);
    }

    @Test
    public void summary_derivesOpenTotalsFromStageRows() {

        // given
        given(dealRepository.pipelineByStage(any())).willReturn(List.of(
                new StageRow(DealStage.PROSPECT, 2L, new BigDecimal("350.00")),
                new StageRow(DealStage.PROPOSAL, 1L, new BigDecimal("400.50"))));

        // when
        DashboardSummaryResponse summary = dashboardService.summary(manager());

        // then — headline numbers come from the rows already fetched, not a seventh query
        assertThat(summary.openDealCount()).isEqualTo(3L);
        assertThat(summary.openPipelineValue()).isEqualByComparingTo(new BigDecimal("750.50"));
    }

    @Test
    public void summary_returnsNullWinRateWhenNothingHasClosed() {

        // given — closedByOutcome is left unstubbed, so Mockito hands back an empty
        // list: exactly what a fresh database returns

        // when
        DashboardSummaryResponse summary = dashboardService.summary(manager());

        // then — null, not ZERO. Nothing has closed yet, which is not a 0% win rate.
        assertThat(summary.winRate()).isNull();
        assertThat(summary.wonCount()).isZero();
        assertThat(summary.lostCount()).isZero();
        assertThat(summary.wonValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void summary_computesWinRateToFourDecimals() {

        // given — 2 won, 1 lost: a ratio that does not terminate
        given(dealRepository.closedByOutcome(any())).willReturn(List.of(
                new OutcomeRow(DealOutcome.WON, 2L, new BigDecimal("1000.00")),
                new OutcomeRow(DealOutcome.LOST, 1L, new BigDecimal("300.00"))));

        // when
        DashboardSummaryResponse summary = dashboardService.summary(manager());

        // then — 0.6666... rounded HALF_UP at scale 4. Without an explicit scale,
        // BigDecimal.divide would have thrown ArithmeticException instead.
        assertThat(summary.winRate()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(summary.wonCount()).isEqualTo(2L);
        assertThat(summary.wonValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(summary.lostCount()).isEqualTo(1L);
    }

    @Test
    public void summary_passesNullOwnerIdForPrivilegedUser() {

        // given
        ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);

        // when
        dashboardService.summary(manager());

        // then — null is the "no restriction" branch: a manager sees the whole org
        verify(dealRepository).pipelineByStage(ownerId.capture());
        assertThat(ownerId.getValue()).isNull();
        verify(dealRepository).closedByOutcome(null);
    }

    @Test
    public void summary_passesOwnIdForSalesRep() {

        // given
        ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);

        // when
        dashboardService.summary(salesRep(7L));

        // then — the leak test. If this ever captures null, every rep is reading
        // org-wide totals and nothing else in the app would show it.
        verify(dealRepository).pipelineByStage(ownerId.capture());
        assertThat(ownerId.getValue()).isEqualTo(7L);
        verify(dealRepository).closedByOutcome(7L);
    }
}
