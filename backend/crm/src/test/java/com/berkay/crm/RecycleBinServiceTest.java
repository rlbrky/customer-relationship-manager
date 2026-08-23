package com.berkay.crm;

import com.berkay.crm.dto.DeletedAccountResponse;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.AccountService;
import com.berkay.crm.service.RecycleBinService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Same commit dance as AccountAuditServiceTest: Envers writes on COMMIT, and the
 * "who deleted it" column is read back out of the audit log, so a rolled-back test
 * would see nothing. Rows outlive the method, so every assertion is scoped to ids
 * this test created and fixtures use per-test usernames.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RecycleBinServiceTest {

    @Autowired private RecycleBinService recycleBinService;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @PersistenceContext private EntityManager entityManager;

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

    private DeletedAccountResponse rowFor(Long id) {
        return recycleBinService.deletedAccounts().stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("account " + id + " not in the recycle bin"));
    }

    @Test
    public void deletedAccounts_listsOnlySoftDeletedOnes() {

        // given — one live, one deleted
        CrmUser owner = newUser("bin1", Roles.MANAGER);
        Account live = newAccount(owner, "Still here");
        Account gone = newAccount(owner, "Removed");
        commit();

        accountService.delete(gone.getId(), owner);
        commit();

        // when
        List<DeletedAccountResponse> deleted = recycleBinService.deletedAccounts();

        // then
        assertThat(deleted).extracting(DeletedAccountResponse::id).contains(gone.getId());
        assertThat(deleted).extracting(DeletedAccountResponse::id).doesNotContain(live.getId());

        // the native projection has to convert DATETIME(6) into an Instant
        assertThat(rowFor(gone.getId()).deletedAt()).isNotNull();
        assertThat(rowFor(gone.getId()).ownerName()).isEqualTo("bin1");
    }

    @Test
    @WithMockUser(username = "carol")
    public void deletedAccounts_reportWhoDeletedThem() {

        // given
        CrmUser owner = newUser("bin2", Roles.MANAGER);
        Account gone = newAccount(owner, "Removed");
        commit();

        accountService.delete(gone.getId(), owner);
        commit();

        // then — @SQLDelete is raw SQL: it never touched last_modified_by, so the
        // live schema structurally cannot answer this. Only the audit log can.
        assertThat(rowFor(gone.getId()).deletedBy()).isEqualTo("carol");
    }

    @Test
    public void restore_makesTheAccountVisibleAgain() {

        // given
        CrmUser owner = newUser("bin3", Roles.MANAGER);
        Account gone = newAccount(owner, "Removed");
        commit();

        accountService.delete(gone.getId(), owner);
        commit();

        assertThat(accountRepository.findById(gone.getId())).isEmpty();   // @SQLRestriction

        // when
        recycleBinService.restore(gone.getId());
        commit();

        // then — an ordinary findById can see it, which is the only proof that the
        // restriction is genuinely satisfied rather than worked around
        assertThat(accountRepository.findById(gone.getId())).isPresent();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void restore_recordsANewRevision() {

        // given
        CrmUser owner = newUser("bin4", Roles.MANAGER);
        Account gone = newAccount(owner, "Removed");
        commit();

        accountService.delete(gone.getId(), owner);
        commit();

        // when
        recycleBinService.restore(gone.getId());
        commit();

        // then — three revisions: ADD, DEL, and the restore. This is the whole reason
        // restore goes through a managed entity instead of a native UPDATE; the
        // native version would have left no trace of the resurrection.
        List<Object[]> rows = AuditReaderFactory.get(entityManager).createQuery()
                .forRevisionsOfEntity(Account.class, false, true)
                .add(AuditEntity.id().eq(gone.getId()))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        assertThat(rows).hasSize(3);
    }

    @Test
    public void restore_doesNotBringBackContacts() {

        // given — a contact swept up by the account's delete cascade
        CrmUser owner = newUser("bin5", Roles.MANAGER);
        Account gone = newAccount(owner, "Removed");
        Contact contact = new Contact();
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setAccount(gone);
        contactRepository.save(contact);
        commit();

        accountService.delete(gone.getId(), owner);
        commit();

        // when
        recycleBinService.restore(gone.getId());
        commit();

        // then — deliberate asymmetry, pinned so nobody "fixes" it by accident.
        // deleted_at cannot say whether this contact went with the cascade or was
        // deleted on purpose a month ago, so restoring it would be a guess.
        assertThat(accountRepository.findById(gone.getId())).isPresent();
        assertThat(contactRepository.findById(contact.getId())).isEmpty();
    }

    @Test
    public void restore_rejectsAnAccountThatIsNotDeleted() {

        // given — alive and well
        CrmUser owner = newUser("bin6", Roles.MANAGER);
        Account live = newAccount(owner, "Still here");
        commit();

        // when / then — the query filters on deleted_at, so a live id is "not found"
        // rather than a silent no-op restore
        assertThatThrownBy(() -> recycleBinService.restore(live.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void deletedAccounts_handleAnAccountDeletedMoreThanOnce() {

        // given — deleted, restored, deleted again: two DEL revisions for one id
        CrmUser owner = newUser("bin7", Roles.MANAGER);
        Account gone = newAccount(owner, "Removed");
        commit();

        accountService.delete(gone.getId(), owner);
        commit();
        recycleBinService.restore(gone.getId());
        commit();
        accountService.delete(gone.getId(), owner);
        commit();

        // then — Collectors.toMap without a merge function throws IllegalStateException
        // on the duplicate id. Restore is what makes two DEL revisions possible at all,
        // so this feature created the collision it has to survive.
        assertThat(rowFor(gone.getId()).deletedBy()).isNotNull();
    }
}
