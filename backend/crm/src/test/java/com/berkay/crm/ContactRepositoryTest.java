package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.ContactSpecifications;
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

    private Contact newContact(Account account, String firstName, String lastName) {
        Contact contact = new Contact();
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail("contact@example.com");
        contact.setAccount(account);
        return contact;
    }

    private Contact newContact(Account account, String firstName, String lastName, String email) {
        Contact contact = new Contact();
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail(email);
        contact.setAccount(account);
        return contact;
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
    public void findByAccountId_returnsContacts() {

        // given
        CrmUser user = newUser("test", "test@example.com", "ROLE_SALES_REP");
        Account account = accountRepository.save(newAccount(user));
        contactRepository.save(newContact(account));

        entityManager.flush();
        entityManager.clear(); // force real DB read.

        // when
        Page<Contact> contacts = contactRepository.findAll(
                ContactSpecifications.inAccount(account.getId()), Pageable.unpaged()
        );

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
        CrmUser user = newUser("test", "test@example.com", "ROLE_SALES_REP");
        Account account = accountRepository.save(newAccount(user));

        contactRepository.save(newContact(account));
        Contact removed = contactRepository.save(newContact(account));

        contactRepository.delete(removed);
        entityManager.flush();
        entityManager.clear();

        var found = contactRepository.findAll(
                ContactSpecifications.inAccount(account.getId()), Pageable.unpaged());

        // when / then
        assertThat(found.getContent())
                .hasSize(1);
    }

    @Test
    public void countByAccountIdIn_excludesSoftDeletedContacts() {

        // given - three contacts 1 soft deleted
        CrmUser user = newUser("test", "test@example.com", "ROLE_SALES_REP");
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

    @Test
    public void matches_findsByFirstOrLastOrEmail() {

        // given
        CrmUser user = newUser("test", "test@example.com", "ROLE_SALES_REP");
        Account account = accountRepository.save(newAccount(user));

        Contact contact1 = contactRepository.save(newContact(account, "John", "Smith", "js@example.com"));
        Contact contact2 = contactRepository.save(newContact(account, "Bob", "Johnson", "bj@example.com"));
        Contact contact3 = contactRepository.save(newContact(account, "Alice", "Cooper", "alice@johndoe.com"));
        Contact contact4 = contactRepository.save(newContact(account, "Jane", "Roe", "jr@example.com"));

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Contact> found = contactRepository.findAll(
                ContactSpecifications.matches("john"), Pageable.unpaged()
        );

        // then - all three hits
        assertThat(found.getContent())
                .extracting(Contact::getLastName)
                .containsExactlyInAnyOrder("Smith", "Johnson", "Cooper");
    }

    @Test
    public void scopedSearch_doesNotLeakAcrossAccounts() {

        // given
        CrmUser rep = newUser("rep", "rep@example.com", "ROLE_SALES_REP");

        Account a1 = accountRepository.save(newAccount(rep, "Alpha", "Technology"));
        Account a2 = accountRepository.save(newAccount(rep, "Beta", "Finance"));

        contactRepository.save(newContact(a1, "John", "Alpha"));
        contactRepository.save(newContact(a2, "John", "Beta"));

        entityManager.flush();
        entityManager.clear();

        // when
        Page<Contact> found = contactRepository.findAll(
                ContactSpecifications.inAccount(a1.getId())
                        .and(ContactSpecifications.matches("john")),
                Pageable.unpaged());

        // then
        assertThat(found.getContent())
                .extracting(Contact::getLastName)
                .containsExactly("Alpha");     // NOT both Johns
    }

    @Test
    public void visibleTo_salesRepSeesOnlyContactsOnOwnAccounts() {

        // given
        CrmUser rep = newUser("rep", "rep@example.com", "ROLE_SALES_REP");
        CrmUser rep2 = newUser("rep2", "rep2@example.com", "ROLE_SALES_REP");

        Account a1 = accountRepository.save(newAccount(rep, "Alpha", "Technology"));
        Account a2 = accountRepository.save(newAccount(rep2, "Beta", "Finance"));

        contactRepository.save(newContact(a1, "John", "Alpha"));
        contactRepository.save(newContact(a2, "John", "Beta"));

        entityManager.flush();
        entityManager.clear();

        // when
        var found = contactRepository.findAll(
                ContactSpecifications.visibleTo(rep), Pageable.unpaged()
        );

        // then - only returns their own
        assertThat(found.getContent())
                .extracting(Contact::getLastName)
                .containsExactly("Alpha");
    }
}
