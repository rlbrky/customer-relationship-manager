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
}
