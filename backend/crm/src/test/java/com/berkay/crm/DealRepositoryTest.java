package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.repository.DealStageHistoryRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.DealSpecifications;
import com.berkay.crm.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class DealRepositoryTest {

    @Autowired private DealRepository dealRepository;
    @Autowired private DealStageHistoryRepository dealStageHistoryRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TestEntityManager entityManager;

    private CrmUser newUser(String username, String roleName) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return userRepository.save(user);
    }

    private Account newAccount(CrmUser owner, String name) {
        Account account = new Account();
        account.setName(name);
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private Deal newDeal(Account account, String title, DealStage stage, BigDecimal value) {
        Deal deal = new Deal();
        deal.setTitle(title);
        deal.setStage(stage);
        deal.setValue(value);
        deal.setAccount(account);
        return dealRepository.save(deal);
    }

    private void newHistory(Deal deal, DealStage from, DealStage to, Instant changedAt) {
        DealStageHistory history = new DealStageHistory();
        history.setDeal(deal);
        history.setFromStage(from);
        history.setToStage(to);
        history.setChangedAt(changedAt);
        history.setChangedBy("tester");
        dealStageHistoryRepository.save(history);
    }

    @Test
    public void value_roundTripsWithoutPrecisionLoss() {

        // given — a value that binary floating point cannot represent exactly
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        Deal deal = newDeal(account, "Renewal", DealStage.PROSPECT, new BigDecimal("1234.56"));

        entityManager.flush();
        entityManager.clear();

        // when
        Deal reloaded = dealRepository.findById(deal.getId()).orElseThrow();

        // then — isEqualByComparingTo, NOT isEqualTo: BigDecimal.equals compares
        // scale too, so 1234.56 and 1234.560 are "different" to equals().
        assertThat(reloaded.getValue()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    public void history_ordersOldestFirst() {

        // given — three transitions recorded out of order
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        Deal deal = newDeal(account, "Renewal", DealStage.NEGOTIATION, null);

        Instant now = Instant.now();
        newHistory(deal, DealStage.QUALIFIED, DealStage.PROPOSAL, now.minus(2, ChronoUnit.DAYS));
        newHistory(deal, null, DealStage.PROSPECT, now.minus(5, ChronoUnit.DAYS));
        newHistory(deal, DealStage.PROPOSAL, DealStage.NEGOTIATION, now.minus(1, ChronoUnit.HOURS));

        entityManager.flush();
        entityManager.clear();

        // when
        var history = dealStageHistoryRepository.findByDealIdOrderByChangedAtAsc(deal.getId());

        // then — the story reads forwards, starting from nowhere
        assertThat(history)
                .extracting(DealStageHistory::getToStage)
                .containsExactly(DealStage.PROSPECT, DealStage.PROPOSAL, DealStage.NEGOTIATION);
        assertThat(history.get(0).getFromStage()).isNull();
    }

    @Test
    public void visibleTo_salesRepSeesOnlyOwnAccountsDeals() {

        // given — two accounts with different owners, one deal each
        CrmUser rep = newUser("rep", Roles.SALES_REP);
        CrmUser other = newUser("other", Roles.SALES_REP);
        newDeal(newAccount(rep, "Mine"), "My deal", DealStage.PROSPECT, null);
        newDeal(newAccount(other, "Theirs"), "Their deal", DealStage.PROSPECT, null);

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Deal> found = dealRepository.findAll(
                DealSpecifications.visibleTo(rep), Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Deal::getTitle)
                .containsExactly("My deal");
    }

    @Test
    public void isOpen_separatesOpenFromClosedDeals() {

        // given — one open deal, one won
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "Still working", DealStage.PROPOSAL, null);
        Deal won = newDeal(account, "Signed", DealStage.NEGOTIATION, null);
        won.setOutcome(DealOutcome.WON);
        won.setClosedAt(Instant.now());

        entityManager.flush();
        entityManager.clear();

        // when / then — outcome IS NULL is the definition of "open"
        assertThat(dealRepository.findAll(DealSpecifications.isOpen(true), Pageable.unpaged())
                .getContent())
                .extracting(Deal::getTitle)
                .containsExactly("Still working");

        assertThat(dealRepository.findAll(DealSpecifications.isOpen(false), Pageable.unpaged())
                .getContent())
                .extracting(Deal::getTitle)
                .containsExactly("Signed");
    }

    // ── M10 aggregates ───────────────────────────────────────────────────────

    private Deal newClosedDeal(Account account, String title, DealStage stage,
                               BigDecimal value, DealOutcome outcome) {
        Deal deal = newDeal(account, title, stage, value);
        deal.setOutcome(outcome);
        deal.setClosedAt(Instant.now());
        return deal;
    }

    /** Pulls one stage's row out of an aggregate result, failing loudly if absent. */
    private DealRepository.StageTotal stageRow(List<DealRepository.StageTotal> totals,
                                               DealStage stage) {

        return totals.stream()
                .filter(row -> row.getStage() == stage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for stage " + stage));
    }

    private DealRepository.OutcomeTotal outcomeRow(List<DealRepository.OutcomeTotal> totals,
                                                   DealOutcome outcome) {
        
        return totals.stream()
                .filter(row -> row.getOutcome() == outcome)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for outcome " + outcome));
    }

    @Test
    public void pipelineByStage_returnsOneRowPerStageThatHasOpenDeals() {

        // given — two of the four stages are in use
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "First", DealStage.PROSPECT, new BigDecimal("100.00"));
        newDeal(account, "Second", DealStage.PROSPECT, new BigDecimal("250.00"));
        newDeal(account, "Third", DealStage.PROPOSAL, new BigDecimal("400.00"));

        entityManager.flush();
        entityManager.clear();

        // when
        List<DealRepository.StageTotal> totals = dealRepository.pipelineByStage(null);

        // then — two rows, not four. GROUP BY never invents an empty group, so
        // QUALIFIED and NEGOTIATION are absent rather than zero. Filling those in
        // is the service's job; this pins the raw behaviour it has to compensate for.
        // containsExactlyInAnyOrder, not containsExactly: GROUP BY promises no order.
        assertThat(totals)
                .extracting(DealRepository.StageTotal::getStage)
                .containsExactlyInAnyOrder(DealStage.PROSPECT, DealStage.PROPOSAL);

        assertThat(stageRow(totals, DealStage.PROSPECT).getDealCount()).isEqualTo(2);
        assertThat(stageRow(totals, DealStage.PROSPECT).getTotalValue())
                .isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    public void pipelineByStage_excludesClosedDeals() {

        // given — an open and a won deal sitting in the same stage
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "Still working", DealStage.NEGOTIATION, new BigDecimal("100.00"));
        newClosedDeal(account, "Signed", DealStage.NEGOTIATION,
                new BigDecimal("999.00"), DealOutcome.WON);

        entityManager.flush();
        entityManager.clear();

        // when
        List<DealRepository.StageTotal> totals = dealRepository.pipelineByStage(null);

        // then — won money must not inflate the open pipeline
        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).getDealCount()).isEqualTo(1);
        assertThat(totals.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    public void pipelineByStage_countsUnpricedDealWithoutSummingIt() {

        // given — two deals in one stage, only one of them priced
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "Priced", DealStage.QUALIFIED, new BigDecimal("500.00"));
        newDeal(account, "Unpriced", DealStage.QUALIFIED, null);

        entityManager.flush();
        entityManager.clear();

        // when
        List<DealRepository.StageTotal> totals = dealRepository.pipelineByStage(null);

        // then — count and sum disagree on purpose: SUM skips NULLs
        assertThat(totals.get(0).getDealCount()).isEqualTo(2);
        assertThat(totals.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    public void pipelineByStage_returnsZeroWhenEveryValueInTheStageIsNull() {

        // given — a stage where nothing has been priced yet
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "Unpriced", DealStage.PROSPECT, null);

        entityManager.flush();
        entityManager.clear();

        // when
        List<DealRepository.StageTotal> totals = dealRepository.pipelineByStage(null);

        // then — SUM over all-NULL is NULL, not 0. That is the only thing COALESCE
        // is buying: without it the projection hands the service a null BigDecimal.
        assertThat(totals.get(0).getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    public void pipelineByStage_excludesSoftDeletedDeals() {

        // given — two deals in one stage, one of them deleted
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newDeal(account, "Alive", DealStage.PROSPECT, new BigDecimal("50.00"));
        Deal doomed = newDeal(account, "Doomed", DealStage.PROSPECT, new BigDecimal("100.00"));

        entityManager.flush();
        dealRepository.delete(doomed);   // @SQLDelete → UPDATE deal SET deleted_at = NOW()
        entityManager.flush();
        entityManager.clear();           // drop the REMOVED-state instance from the session

        // when
        List<DealRepository.StageTotal> totals = dealRepository.pipelineByStage(null);

        // then — @SQLRestriction reaches JPQL aggregates, not just findById
        assertThat(totals.get(0).getDealCount()).isEqualTo(1);
        assertThat(totals.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    public void pipelineByStage_scopesToOwnerWhenOwnerIdGiven() {

        // given — the same stage, two owners
        CrmUser rep = newUser("rep", Roles.SALES_REP);
        CrmUser other = newUser("other", Roles.SALES_REP);
        newDeal(newAccount(rep, "Mine"), "My deal", DealStage.PROSPECT, new BigDecimal("100.00"));
        newDeal(newAccount(other, "Theirs"), "Their deal", DealStage.PROSPECT, new BigDecimal("900.00"));

        entityManager.flush();
        entityManager.clear();

        // when — both branches of ":ownerId is null"
        List<DealRepository.StageTotal> scoped = dealRepository.pipelineByStage(rep.getId());
        List<DealRepository.StageTotal> unrestricted = dealRepository.pipelineByStage(null);

        // then — the null branch is the one that silently leaks org-wide totals
        // to a sales rep if the predicate is written wrong
        assertThat(scoped.get(0).getDealCount()).isEqualTo(1);
        assertThat(scoped.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(unrestricted.get(0).getDealCount()).isEqualTo(2);
        assertThat(unrestricted.get(0).getTotalValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    public void closedByOutcome_separatesWonFromLostAndIgnoresOpenDeals() {

        // given — one won, two lost, one still open
        CrmUser user = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(user, "Acme");
        newClosedDeal(account, "Signed", DealStage.NEGOTIATION,
                new BigDecimal("1000.00"), DealOutcome.WON);
        newClosedDeal(account, "Went elsewhere", DealStage.PROPOSAL,
                new BigDecimal("300.00"), DealOutcome.LOST);
        newClosedDeal(account, "No budget", DealStage.QUALIFIED,
                new BigDecimal("200.00"), DealOutcome.LOST);
        newDeal(account, "Still working", DealStage.PROSPECT, new BigDecimal("777.00"));

        entityManager.flush();
        entityManager.clear();

        // when
        List<DealRepository.OutcomeTotal> totals = dealRepository.closedByOutcome(null);

        // then — grouped by outcome, so the two lost deals collapse into one row
        // and the three different stages they closed from stop mattering
        assertThat(totals)
                .extracting(DealRepository.OutcomeTotal::getOutcome)
                .containsExactlyInAnyOrder(DealOutcome.WON, DealOutcome.LOST);

        assertThat(outcomeRow(totals, DealOutcome.WON).getDealCount()).isEqualTo(1);
        assertThat(outcomeRow(totals, DealOutcome.WON).getTotalValue())
                .isEqualByComparingTo(new BigDecimal("1000.00"));

        assertThat(outcomeRow(totals, DealOutcome.LOST).getDealCount()).isEqualTo(2);
        assertThat(outcomeRow(totals, DealOutcome.LOST).getTotalValue())
                .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    public void closedByOutcome_scopesToOwnerWhenOwnerIdGiven() {

        // given — a won deal each, different owners
        CrmUser rep = newUser("rep", Roles.SALES_REP);
        CrmUser other = newUser("other", Roles.SALES_REP);
        newClosedDeal(newAccount(rep, "Mine"), "Mine", DealStage.NEGOTIATION,
                new BigDecimal("100.00"), DealOutcome.WON);
        newClosedDeal(newAccount(other, "Theirs"), "Theirs", DealStage.NEGOTIATION,
                new BigDecimal("900.00"), DealOutcome.WON);

        entityManager.flush();
        entityManager.clear();

        // when / then — a second query means a second copy of the owner predicate,
        // which can be wrong independently of pipelineByStage's
        assertThat(outcomeRow(dealRepository.closedByOutcome(rep.getId()), DealOutcome.WON)
                .getDealCount()).isEqualTo(1);
        assertThat(outcomeRow(dealRepository.closedByOutcome(null), DealOutcome.WON)
                .getDealCount()).isEqualTo(2);
    }
}
