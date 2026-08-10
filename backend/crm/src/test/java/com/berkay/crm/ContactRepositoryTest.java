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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class ContactRepositoryTest {

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

    private Contact newContact(Account account) {
        Contact contact = new Contact();
        contact.setFirstName("Test");
        contact.setLastName("User");
        contact.setEmail("contact@example.com");
        contact.setAccount(account);
        return contact;
    }

    private Account newAccount(CrmUser owner) {
        Account account = new Account();
        account.setName("Test Corp");
        account.setOwner(owner);
        return account;
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
        Page<Contact> contacts = contactRepository.findByAccountId(account.getId(), Pageable.unpaged());

        // then
        assertThat(contacts.getContent())
                .hasSize(1)
                .first()
                .extracting(Contact::getEmail)
                .isEqualTo("contact@example.com");
    }

    @Test
    public void findByAccountId_excludesSoftDeletedContacts() {

        // given
        CrmUser user = newUser("test", "test@example.com");
        Account account = accountRepository.save(newAccount(user));

        contactRepository.save(newContact(account));
        Contact removed = contactRepository.save(newContact(account));

        contactRepository.delete(removed);
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(contactRepository.findByAccountId(account.getId(), Pageable.unpaged()).getContent())
                .hasSize(1);
    }

    @Test
    public void countByAccountIdIn_excludesSoftDeletedContacts() {

        // given - three contacts 1 soft deleted
        CrmUser user = newUser("test", "test@example.com");
        Account account = accountRepository.save(newAccount(user));
        contactRepository.save(newContact(account));
        contactRepository.save(newContact(account));
        Contact removed = contactRepository.save(newContact(account));

        contactRepository.delete(removed);
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(contactRepository.countByAccountIdIn(List.of(account.getId())))
                .singleElement()
                .extracting(ContactRepository.AccountContactCount::getTotal)
                .isEqualTo(2L);
    }
}
