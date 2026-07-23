package com.berkay.crm;

import com.berkay.crm.config.JpaAuditingConfig;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
public class TestDomainFoundation {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TestEntityManager entityManager;

    // To ensure the tests are done correctly.
    private CrmUser newUser(String username, String email) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        return user;
    }

    @Test
    public void save_populatesAuditFields() {

        // given
        CrmUser savedUser = userRepository.saveAndFlush(newUser("test", "test@example.com"));

        // then
        assertThat(savedUser.getCreatedBy()).isNotNull();
        assertThat(savedUser.getCreatedDate()).isNotNull();
        assertThat(savedUser.getLastModifiedDate()).isNotNull();
        assertThat(savedUser.getVersion()).isZero();
    }

    @Test
    public void save_rejectsDuplicateEmail() {

        // given
        userRepository.saveAndFlush(newUser("test", "test@example.com"));

        CrmUser secondUser = newUser("test2", "test@example.com");

        // then
        assertThatThrownBy(() -> userRepository.save(secondUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    public void save_rejectsDuplicateUsername() {

        // given
        userRepository.saveAndFlush(newUser("test", "test@example.com"));

        // when
        CrmUser second = newUser("test", "test2@example.com");

        // then
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    public void assignRoles_persistsJoinTable() {

        // given
        Role admin = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow();

        CrmUser user = newUser("test", "test@example.com");

        user.getRoles().add(admin);

        // when
        CrmUser savedUser = userRepository.saveAndFlush(user);
        entityManager.clear();

        // then
        CrmUser reloaded = userRepository.findById(savedUser.getId())
                .orElseThrow();

        assertThat(reloaded.getRoles()).extracting(Role::getName).containsExactly("ROLE_ADMIN");
    }

    @Test
    public void update_incrementsVersion() {

        // given
        CrmUser savedUser = userRepository.saveAndFlush(newUser("test", "test@example.com"));
        assertThat(savedUser.getVersion()).isZero();

        // when
        savedUser.setFirstName("Changed");
        CrmUser updatedUser = userRepository.saveAndFlush(savedUser);

        // then
        assertThat(updatedUser.getVersion()).isEqualTo(1);
    }
}
