package com.berkay.crm.config;

import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
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

    public DataSeeder(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {

        // To not reinsert this user every time the app runs.
        if(userRepository.existsByUsername("admin")) return;

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
}
