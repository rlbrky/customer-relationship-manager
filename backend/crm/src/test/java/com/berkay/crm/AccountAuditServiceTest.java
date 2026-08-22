package com.berkay.crm;

import com.berkay.crm.dto.FieldChange;
import com.berkay.crm.dto.RevisionResponse;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.CurrentUsername;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.AccountAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Envers writes its audit rows on transaction COMMIT. @DataJpaTest would roll every
 * test back, so nothing would ever reach the _AUD tables and every assertion would
 * fail looking exactly like a broken query. Hence the explicit commit boundaries.
 *
 * Consequence to design around: committed rows outlive the test method, so the class
 * shares one database. Every assertion is scoped to an id created by that test, and
 * fixtures use per-test usernames so the unique constraints don't collide.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")   // keeps DataSeeder out — its writes would add revisions of their own
@Transactional
public class AccountAuditServiceTest {

    @Autowired private AccountAuditService accountAuditService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @PersistenceContext private EntityManager entityManager;

    /** One commit is exactly one Envers revision — this is how a history is built. */
    private void commit() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

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
        account.setIndustry("Technology");
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private FieldChange changeOf(RevisionResponse revision, String field) {
        return revision.changes().stream()
                .filter(change -> change.field().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no change recorded for " + field));
    }

    @Test
    public void revisions_recordInsertAsFirstRevision() {

        // given
        CrmUser owner = newUser("audit1", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        // when
        List<RevisionResponse> revisions = accountAuditService.revisions(account.getId(), owner);

        // then — every field set on insert reads as an initial value, from null
        assertThat(revisions).hasSize(1);
        assertThat(revisions.get(0).type()).isEqualTo("ADD");
        assertThat(changeOf(revisions.get(0), "name").from()).isNull();
        assertThat(changeOf(revisions.get(0), "name").to()).isEqualTo("Acme");
    }

    @Test
    public void revisions_recordFieldLevelChanges() {

        // given — two commits, so two revisions to diff against each other
        CrmUser owner = newUser("audit2", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        Account managed = accountRepository.findById(account.getId()).orElseThrow();
        managed.setName("Acme Ltd");
        commit();

        // when
        List<RevisionResponse> revisions = accountAuditService.revisions(account.getId(), owner);

        // then — newest first, and only the field that actually moved is listed
        assertThat(revisions).hasSize(2);
        RevisionResponse latest = revisions.get(0);
        assertThat(latest.type()).isEqualTo("MOD");
        assertThat(latest.changes()).extracting(FieldChange::field).containsExactly("name");
        assertThat(changeOf(latest, "name").from()).isEqualTo("Acme");
        assertThat(changeOf(latest, "name").to()).isEqualTo("Acme Ltd");
    }

    @Test
    @WithMockUser(username = "alice")
    public void revisions_captureTheAuthenticatedUsername() {

        // given — the listener reads SecurityContextHolder on this same thread
        CrmUser owner = newUser("audit3", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        // when
        List<RevisionResponse> revisions = accountAuditService.revisions(account.getId(), owner);

        // then — not the "system-user" fallback
        assertThat(revisions.get(0).changedBy()).isEqualTo("alice");
        assertThat(revisions.get(0).changedBy()).isNotEqualTo(CurrentUsername.SYSTEM);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void softDelete_isRecordedAsDeletionEvenThoughTheRowRemains() {

        // given
        CrmUser owner = newUser("audit4", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        accountRepository.delete(accountRepository.findById(account.getId()).orElseThrow());
        commit();

        // when — read through AuditReader directly, because the service guard cannot
        // see a soft-deleted account (pinned by the next test)
        List<Object[]> rows = AuditReaderFactory.get(entityManager).createQuery()
                .forRevisionsOfEntity(Account.class, false, true)
                .add(AuditEntity.id().eq(account.getId()))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        // then — DEL, not MOD. @SQLDelete rewrites the SQL Hibernate emits, but Envers
        // hooks the entity lifecycle EVENT, which sits above that rewrite and is still
        // a delete. The two views of the same act disagree, and both are right:
        assertThat(rows).hasSize(2);
        assertThat((RevisionType) rows.get(1)[2]).isEqualTo(RevisionType.DEL);

        // ... the audit log says deleted, while the row is still physically there.
        Number liveRows = (Number) entityManager
                .createNativeQuery("select count(*) from account where id = :id")
                .setParameter("id", account.getId())
                .getSingleResult();
        assertThat(liveRows.intValue()).isEqualTo(1);
    }

    @Test
    public void revisions_areUnavailableOnceTheAccountIsSoftDeleted() {

        // given
        CrmUser owner = newUser("audit8", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        accountRepository.delete(accountRepository.findById(account.getId()).orElseThrow());
        commit();

        // when / then — the audit rows still exist and @SQLRestriction does not hide
        // them, but the guard runs through loadAccessible, which cannot see the
        // deleted account. Deliberate for now; 11c's recycle bin needs its own guard
        // precisely because this one refuses.
        assertThatThrownBy(() -> accountAuditService.revisions(account.getId(), owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void revisions_doNotFireWhenAContactIsAdded() {

        // given
        CrmUser owner = newUser("audit5", Roles.MANAGER);
        Account account = newAccount(owner, "Acme");
        commit();

        Contact contact = new Contact();
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setAccount(accountRepository.findById(account.getId()).orElseThrow());
        contactRepository.save(contact);
        commit();

        // when
        List<RevisionResponse> revisions = accountAuditService.revisions(account.getId(), owner);

        // then — still one. This is what @NotAudited on the inverse collections buys:
        // without it every contact added would append a revision to the account in
        // which nothing about the account changed.
        assertThat(revisions).hasSize(1);
    }

    @Test
    public void revisions_deniedForAnotherOwnersAccount() {

        // given
        CrmUser owner = newUser("audit6", Roles.SALES_REP);
        CrmUser intruder = newUser("audit7", Roles.SALES_REP);
        Account account = newAccount(owner, "Acme");
        commit();

        // when / then — the guard runs before AuditReader. @SQLRestriction does not
        // reach the audit tables, so nothing else would stop this read.
        assertThatThrownBy(() -> accountAuditService.revisions(account.getId(), intruder))
                .isInstanceOf(AccessDeniedException.class);
    }
}
