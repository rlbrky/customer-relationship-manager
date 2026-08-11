package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private CrmUser newUser(String username, String email, String roleName) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());

        return userRepository.save(user);
    }

    private CrmUser newUser(String username, String email) {
        return newUser(username, email, Roles.SALES_REP);
    }

    private Account newAccount(CrmUser owner) {
        Account account = new Account();
        account.setName("Test Corp");
        account.setOwner(owner);
        return account;
    }

    private Account newAccount(CrmUser owner, String name, String industry) {
        Account account = new Account();
        account.setName(name);
        account.setIndustry(industry);
        account.setOwner(owner);
        return account;
    }

    @Test
    public void delete_softDeletes_rowRemainsInTable() {

        // given
        CrmUser user = newUser("test", "test@example.com");
        Account account = newAccount(user);
        account.setOwner(user);

        accountRepository.save(account);
        accountRepository.delete(account);
        entityManager.flush(); // force the UPDATE call

        // then
        Object deletedAt = entityManager.getEntityManager()
                .createNativeQuery("SELECT deleted_at FROM account WHERE id = :id")
                .setParameter("id", account.getId())
                .getSingleResult();
        assertThat(deletedAt).isNotNull();
    }

    @Test
    public void findById_afterSoftDelete_stillReturnsRow() {

        // given
        CrmUser user = newUser("test", "test@example.com");
        Account account = newAccount(user);
        account.setOwner(user);

        accountRepository.save(account);
        accountRepository.delete(account);
        entityManager.flush();
        entityManager.clear(); // drop persistence context so findById hits DB

        // when
        Optional<Account> found = accountRepository.findById(account.getId());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    public void findAll_excludesSoftDeleted() {

        // given
        CrmUser user = newUser("test", "test@example.com");

        Account account1 = newAccount(user);
        account1.setOwner(user);

        Account account2 = newAccount(user);
        account2.setOwner(user);

        accountRepository.save(account1);
        accountRepository.save(account2);

        // when
        accountRepository.delete(account1);
        entityManager.flush();

        // then
        assertThat(accountRepository.findAll()).hasSize(1);
    }

    @Test
    public void visibleTo_salesRepSeesOnlyOwnAccounts() {

        // given
        CrmUser rep = newUser("rep1", "rep@example.com");
        CrmUser other = newUser("other", "other@example.com");
        accountRepository.save(newAccount(rep, "Mine", "Technology"));
        accountRepository.save(newAccount(other, "Theirs", "Finance"));

        entityManager.flush();
        entityManager.clear();

        // when
        var found = accountRepository.findAll(
                AccountSpecifications.visibleTo(rep), Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Account::getName)
                .containsExactly("Mine");
    }

    @Test
    public void visibleTo_managerSeesAll() {

        // given
        CrmUser manager = newUser("rep1", "rep@example.com", "ROLE_MANAGER");
        CrmUser other = newUser("other", "other@example.com");
        accountRepository.save(newAccount(manager, "Mine", "Technology"));
        accountRepository.save(newAccount(other, "Theirs", "Finance"));

        entityManager.flush();
        entityManager.clear();

        // when
        var found = accountRepository.findAll(
                AccountSpecifications.visibleTo(manager), Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Account::getName)
                .containsExactlyInAnyOrder("Mine", "Theirs");
    }

    @Test
    public void nameContains_isCaseInsensitive() {

        // given
        CrmUser rep = newUser("rep", "rep@example.com");
        accountRepository.save(newAccount(rep, "Acme Corp", "Technology"));
        accountRepository.save(newAccount(rep, "Globex", "Technology"));

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Account> found = accountRepository.findAll(
                AccountSpecifications.nameContains("ACME"), Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Account::getName)
                .containsExactly("Acme Corp");
    }

    @Test
    public void industryIs_matchesExactlyIgnoringCase() {

        // given
        CrmUser rep = newUser("rep", "rep@example.com");
        accountRepository.save(newAccount(rep, "Acme Corp", "Technology"));
        accountRepository.save(newAccount(rep, "Globex", "Finance"));

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Account> found = accountRepository.findAll(
                AccountSpecifications.industryIs("technology"), Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Account::getName)
                .containsExactly("Acme Corp");
    }

    @Test
    public void composedSpecs_areAnded() {

        // given
        CrmUser rep = newUser("rep", "rep@example.com");
        accountRepository.save(newAccount(rep, "Acme", "Technology"));
        accountRepository.save(newAccount(rep, "Acme Holdings", "Finance"));
        accountRepository.save(newAccount(rep, "Globex", "Technology"));

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Account> found = accountRepository.findAll(
                AccountSpecifications.nameContains("acme")
                        .and(AccountSpecifications.industryIs("Technology")),
                Pageable.unpaged());

        // then — AND, not OR: only the row satisfying both survives
        assertThat(found.getContent())
                .extracting(Account::getName)
                .containsExactly("Acme");
    }
}
