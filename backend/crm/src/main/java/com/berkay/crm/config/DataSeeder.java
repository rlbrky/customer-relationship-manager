package com.berkay.crm.config;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final AccountRepository accountRepository;

    public DataSeeder(UserRepository userRepository, RoleRepository roleRepository,
                      PasswordEncoder passwordEncoder, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountRepository = accountRepository;
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
    }
}
