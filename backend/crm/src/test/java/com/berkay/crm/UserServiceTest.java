package com.berkay.crm;

import com.berkay.crm.dto.UserCreateRequest;
import com.berkay.crm.dto.UserResponse;
import com.berkay.crm.dto.UserUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    private Role roleNamed(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private Role salesRepRole() { return roleNamed("ROLE_SALES_REP"); }

    private CrmUser userWith(String username, String email) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setVersion(0);   // a persisted row always has one; null would fail every version check
        return user;
    }

    @Test
    void create_encodesPassword_neverStoresRaw(){
        // given
        given(userRepository.existsByUsername("jdoe")).willReturn(false);
        given(userRepository.existsByEmail("jdoe@example.com")).willReturn(false);

        given(roleRepository.findByName("ROLE_SALES_REP")).willReturn(Optional.of(salesRepRole()));
        given(passwordEncoder.encode("password")).willReturn("HASHED");
        given(userRepository.save(any(CrmUser.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        userService.create(new UserCreateRequest("jdoe", "jdoe@example.com", "password",
                "Jane", "Doe", Set.of("ROLE_SALES_REP")));

        // then
        ArgumentCaptor<CrmUser> captor = ArgumentCaptor.forClass(CrmUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password");
    }

    @Test
    void create_rejectsDuplicateUsername() {

        // given
        given(userRepository.existsByUsername("jdoe")).willReturn(true);

        // when / then
        assertThatThrownBy(() ->
                userService.create(new UserCreateRequest("jdoe", "jdoe@example.com", "password",
                        "Jane", "Doe", Set.of("ROLE_SALES_REP"))))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateEmail() {

        // given
        given(userRepository.existsByUsername("jdoe")).willReturn(false);
        given(userRepository.existsByEmail("jdoe@example.com")).willReturn(true);

        // when / then
        assertThatThrownBy(() ->
                userService.create(new UserCreateRequest("jdoe", "jdoe@example.com", "password",
                        "Jane", "Doe", Set.of("ROLE_SALES_REP"))))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownRole() {

        // given
        given(userRepository.existsByUsername("jdoe")).willReturn(false);
        given(roleRepository.findByName("ROLE_SALES_REP")).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() ->
                userService.create(new UserCreateRequest("jdoe", "jdoe@example.com", "password",
                        "Jane", "Doe", Set.of("ROLE_SALES_REP"))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void update_conflictsOnlyWhenEmailChanged() {

        // given
        CrmUser existing = userWith("jdoe", "old@example.com");

        given(userRepository.findById(1L)).willReturn(Optional.of(existing));
        given(userRepository.existsByEmail("new@example.com")).willReturn(true);

        // when - then
        assertThatThrownBy(() -> userService.update(1L, new UserUpdateRequest(
                0, "new@example.com", "Jane", "Doe", true, Set.of("ROLE_SALES_REP")
                )
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("Email");   // not the version conflict, which throws the same type
    }

    @Test
    void update_allowsSameEmailAndReplacesRoles() {

        // given
        CrmUser existing = userWith("jdoe", "jdoe@example.com");
        existing.getRoles().add(roleNamed("ROLE_MANAGER"));               // starts as MANAGER

        given(userRepository.findById(1L)).willReturn(Optional.of(existing));
        given(roleRepository.findByName("ROLE_SALES_REP")).willReturn(Optional.of(salesRepRole()));
        // note: existsByEmail is NOT stubbed — email is unchanged, so the code must skip that check

        // when
        userService.update(1L, new UserUpdateRequest(
                0, "jdoe@example.com", "Jane", "Doe", true, Set.of("ROLE_SALES_REP")));

        // then
        assertThat(existing.getRoles()).extracting(Role::getName).containsExactly("ROLE_SALES_REP");
        assertThat(existing.getFirstName()).isEqualTo("Jane");
    }

    @Test
    void update_rejectsStaleVersion() {

        // given — the stored row has moved on since the caller read it
        CrmUser existing = userWith("jdoe", "jdoe@example.com");
        existing.setVersion(5);

        given(userRepository.findById(1L)).willReturn(Optional.of(existing));
        // existsByEmail is NOT stubbed: the version check has to short-circuit before it

        // when / then
        assertThatThrownBy(() -> userService.update(1L, new UserUpdateRequest(
                3, "different@example.com", "Jane", "Doe", true, Set.of("ROLE_SALES_REP"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reload");

        // nothing was written
        assertThat(existing.getFirstName()).isEqualTo("Test");
        assertThat(existing.getEmail()).isEqualTo("jdoe@example.com");
    }

    @Test
    void deactivate_preventsSelfLockout() {

        assertThatThrownBy(() -> userService.deactivate(5L, 5L)) // same id = acting on self
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void deactivate_disablesUser() {

        // given
        CrmUser existing = userWith("jdoe", "jdoe@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(existing));

        // when
        userService.deactivate(1L, 99L); // different user acts on existing user

        // then
        assertThat(existing.isEnabled()).isFalse();
    }
}
