package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.*;
import com.berkay.crm.repository.specification.ActivitySpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class ActivityRepositoryTest {

    @Autowired private ActivityRepository activityRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager entityManager;

    private CrmUser newUser(String username) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private Account newAccount(CrmUser owner, String name) {
        Account account = new Account();
        account.setName(name);
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private Contact newContact(Account account) {
        Contact contact = new Contact();
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setAccount(account);
        return contactRepository.save(contact);
    }

    private Activity newActivity(Account account, Contact contact, String subject, Instant occurredAt) {
        Activity activity = new Activity();
        activity.setAccount(account);
        activity.setContact(contact);
        activity.setType(ActivityType.CALL);
        activity.setSubject(subject);
        activity.setOccurredAt(occurredAt);
        activity.setCompleted(false);
        return activityRepository.save(activity);
    }

    @Test
    public void timeline_ordersByOccurredAtDesc() {
        // given — three activities logged on different days
        CrmUser user = newUser("rep");
        Account account = newAccount(user, "Acme");
        Instant now = Instant.now();
        newActivity(account, null, "Oldest", now.minus(3, ChronoUnit.DAYS));
        newActivity(account, null, "Newest", now.minus(1, ChronoUnit.HOURS));
        newActivity(account, null, "Middle", now.minus(1, ChronoUnit.DAYS));

        entityManager.flush();
        entityManager.clear();

        // when — the timeline's natural order
        Page<Activity> timeline = activityRepository.findAll(
                ActivitySpecifications.inAccount(account.getId()),
                PageRequest.of(0, 10, Sort.by("occurredAt").descending()));

        // then
        assertThat(timeline.getContent())
                .extracting(Activity::getSubject)
                .containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    public void inAccount_excludesOtherAccounts() {
        // given
        CrmUser user = newUser("rep");
        Account mine = newAccount(user, "Mine");
        Account theirs = newAccount(user, "Theirs");
        newActivity(mine, null, "On mine", Instant.now());
        newActivity(theirs, null, "On theirs", Instant.now());

        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(activityRepository.findAll(
                ActivitySpecifications.inAccount(mine.getId()), Pageable.unpaged()).getContent())
                .extracting(Activity::getSubject)
                .containsExactly("On mine");
    }

    @Test
    public void timeline_keepsActivityAfterContactSoftDeleted() {
        // given — an activity linked to a contact
        CrmUser user = newUser("rep");
        Account account = newAccount(user, "Acme");
        Contact contact = newContact(account);
        newActivity(account, contact, "Kickoff call", Instant.now());
        entityManager.flush();
        entityManager.clear(); // the activity leaves the session, as it would between requests

        // when — the person leaves the CRM; the interaction still happened
        Contact managed = contactRepository.findById(contact.getId()).orElseThrow();
        contactRepository.delete(managed);
        entityManager.flush();
        entityManager.clear();

        // then — ???  run it and see
        Page<Activity> timeline = activityRepository.findAll(
                ActivitySpecifications.inAccount(account.getId()), Pageable.unpaged());

        assertThat(timeline.getContent()).hasSize(1);
    }
}