package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.*;
import com.berkay.crm.repository.specification.ActivitySpecifications;
import com.berkay.crm.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class ActivityRepositoryTest {

    @Autowired private ActivityRepository activityRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private RoleRepository roleRepository;
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

    // ── M10 overdue tasks ────────────────────────────────────────────────────

    /** The role matters here: visibleTo branches on it. */
    private CrmUser newUser(String username, String roleName) {

        CrmUser user = newUser(username);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return user;   // managed — the join-table row is written at flush
    }

    private Activity newTask(Account account, String subject, LocalDateTime dueAt, boolean completed) {

        Activity task = new Activity();
        task.setAccount(account);
        task.setType(ActivityType.TASK);
        task.setSubject(subject);
        task.setOccurredAt(Instant.now());
        task.setDueAt(dueAt);
        task.setCompleted(completed);
        return activityRepository.save(task);
    }

    /** The exact composition DashboardService will use, so the test documents it. */
    private Specification<Activity> overdueTasks(CrmUser user, LocalDateTime now) {

        return ActivitySpecifications.visibleTo(user)
                .and(ActivitySpecifications.ofType(ActivityType.TASK))
                .and(ActivitySpecifications.isCompleted(false))
                .and(ActivitySpecifications.dueBefore(now));
    }

    @Test
    public void overdueTasks_countsOnlyIncompletePastDueTasks() {

        // given — one genuinely overdue task among three decoys
        CrmUser rep = newUser("rep", Roles.SALES_REP);
        Account account = newAccount(rep, "Acme");
        LocalDateTime now = LocalDateTime.now();

        newTask(account, "Chase the contract", now.minusDays(1), false);    // the only hit
        newTask(account, "Already handled", now.minusDays(2), true);        // completed
        newTask(account, "Next week", now.plusDays(7), false);              // not due yet
        newActivity(account, null, "A call that happened", Instant.now());  // CALL, dueAt null

        entityManager.flush();
        entityManager.clear();

        // when / then — the call drops out with no explicit guard: SQL's `null < now`
        // is unknown, so an activity with no deadline can never be overdue
        assertThat(activityRepository.count(overdueTasks(rep, now))).isEqualTo(1);
    }

    @Test
    public void overdueTasks_scopeDependsOnRole() {

        // given — an overdue task on each of two owners' accounts
        CrmUser rep = newUser("rep", Roles.SALES_REP);
        CrmUser other = newUser("other", Roles.SALES_REP);
        CrmUser manager = newUser("boss", Roles.MANAGER);
        LocalDateTime now = LocalDateTime.now();

        newTask(newAccount(rep, "Mine"), "Mine", now.minusDays(1), false);
        newTask(newAccount(other, "Theirs"), "Theirs", now.minusDays(1), false);

        entityManager.flush();
        entityManager.clear();

        // when / then — both branches of visibleTo. The manager owns no account at
        // all, so a wrong predicate here reads as 0 rather than as a leak.
        assertThat(activityRepository.count(overdueTasks(rep, now))).isEqualTo(1);
        assertThat(activityRepository.count(overdueTasks(manager, now))).isEqualTo(2);
    }
}
