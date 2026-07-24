package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private CrmUser newUser(String username, String email) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        userRepository.save(user);

        return user;
    }

    private Account newAccount(CrmUser owner) {
        Account account = new Account();
        account.setName("Test Corp");
        account.setOwner(owner);
        return account;
    }

    private Contact newContact(Account account) {
        Contact contact = new Contact();
        contact.setFirstName("Test");
        contact.setLastName("User");
        contact.setEmail("contact@example.com");
        contact.setAccount(account);
        return contact;
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
    public void findByAccountId_returnsContacts() {

        // given
        CrmUser user = newUser("test", "test@example.com");
        Account account = accountRepository.save(newAccount(user));
        contactRepository.save(newContact(account));

        entityManager.flush();
        entityManager.clear(); // force real DB read.

        // when
        List<Contact> contacts = contactRepository.findByAccountId(account.getId());

        // then
        assertThat(contacts)
                .hasSize(1)
                .first()
                .extracting(Contact::getEmail)
                .isEqualTo("contact@example.com");
    }
}
