package com.berkay.crm.config;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Deal;
import com.berkay.crm.model.DealOutcome;
import com.berkay.crm.model.DealStage;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.ActivitySpecifications;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final AccountRepository accountRepository;

    private final DealRepository dealRepository;

    private final ActivityRepository activityRepository;

    public DataSeeder(UserRepository userRepository, RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder, AccountRepository accountRepository,
                      DealRepository dealRepository, ActivityRepository activityRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountRepository = accountRepository;
        this.dealRepository = dealRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {

        // To not reinsert this user every time the app runs.
        if(!userRepository.existsByUsername("admin")) {

            CrmUser user = new CrmUser();
            user.setUsername("admin");
            user.setEmail("admin@crm.local");
            user.setFirstName("Admin");
            user.setLastName("User");
            user.setEnabled(true);
            user.setPasswordHash(passwordEncoder.encode("admin"));
            user.getRoles()
                    .add(roleRepository.findByName("ROLE_ADMIN")
                        .orElseThrow());
            userRepository.save(user);
        }

        if (!userRepository.existsByUsername("rep")) {
            CrmUser rep = new CrmUser();
            rep.setUsername("rep");
            rep.setEmail("rep@crm.local");
            rep.setFirstName("Sales");
            rep.setLastName("Rep");
            rep.setEnabled(true);
            rep.setPasswordHash(passwordEncoder.encode("rep"));
            rep.getRoles().add(roleRepository.findByName("ROLE_SALES_REP").orElseThrow());
            userRepository.save(rep);
        }

        if (accountRepository.count() == 0) {
            CrmUser admin = userRepository.findByUsername("admin").orElseThrow();
            CrmUser rep = userRepository.findByUsername("rep").orElseThrow();

            String[] names = { "Acme Corp", "Globex", "Initech", "Umbrella", "Soylent" };
            for (int i = 0; i < names.length; i++) {
                Account account = new Account();
                account.setName(names[i]);
                account.setIndustry("Technology");
                account.setOwner(i % 2 == 0 ? admin : rep);   // mixed owners
                accountRepository.save(account);
            }
        }

        // Seeded straight through the repository, so these deals have NO stage
        // history — DealService.create is what writes that. History appears once
        // a card is actually moved on the board.
        if (dealRepository.count() == 0) {
            List<Account> accounts = accountRepository.findAll();
            DealStage[] stages = DealStage.values();

            for (int i = 0; i < accounts.size(); i++) {
                Deal deal = new Deal();
                deal.setTitle(accounts.get(i).getName() + " renewal");
                deal.setValue(new BigDecimal("1250.00").multiply(BigDecimal.valueOf(i + 1L)));
                deal.setStage(stages[i % stages.length]);   // spread across every board column
                deal.setExpectedCloseDate(LocalDate.now().plusDays(15L * (i + 1)));
                deal.setAccount(accounts.get(i));
                dealRepository.save(deal);
            }
        }

        // The dashboard's win-rate and overdue tiles need something to report, but
        // every seeded deal is open and there are no tasks anywhere. Guard on
        // "nothing has closed yet" rather than on a row count, so this still fires
        // on a database that already holds deals — and never fires twice.
        List<Deal> deals = dealRepository.findAll();
        if (deals.size() >= 2 && deals.stream().allMatch(deal -> deal.getOutcome() == null)) {
            closeDeal(deals.get(0), DealOutcome.WON);
            closeDeal(deals.get(1), DealOutcome.LOST);
        }

        // Same idea, narrower count: only TASKs, so calls and notes logged by hand
        // in M8 don't suppress the seed.
        if (activityRepository.count(ActivitySpecifications.ofType(ActivityType.TASK)) == 0) {
            List<Account> accounts = accountRepository.findAll();

            for (int i = 0; i < accounts.size(); i++) {
                Activity task = new Activity();
                task.setAccount(accounts.get(i));
                task.setType(ActivityType.TASK);
                task.setSubject("Follow up with " + accounts.get(i).getName());
                task.setOccurredAt(Instant.now());
                // the first two are already late; the rest are still ahead
                task.setDueAt(i < 2
                        ? LocalDateTime.now().minusDays(i + 1L)
                        : LocalDateTime.now().plusDays(i + 1L));
                task.setCompleted(false);
                activityRepository.save(task);
            }
        }
    }

    /** Straight through the repository, so no stage history is written. */
    private void closeDeal(Deal deal, DealOutcome outcome) {

        deal.setOutcome(outcome);
        deal.setClosedAt(Instant.now());
        dealRepository.save(deal);
    }
}
