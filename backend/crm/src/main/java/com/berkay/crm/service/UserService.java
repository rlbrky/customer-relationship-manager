package com.berkay.crm.service;

import com.berkay.crm.dto.UserCreateRequest;
import com.berkay.crm.dto.UserResponse;
import com.berkay.crm.dto.UserUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UserResponse create(UserCreateRequest userCreateRequest) {

        if(userRepository.existsByUsername(userCreateRequest.username())) {
            throw new ConflictException("Username is already in use");
        }

        if(userRepository.existsByEmail(userCreateRequest.email())) {
            throw new ConflictException("Email is already in use");
        }

        CrmUser newUser = new CrmUser();
        newUser.setUsername(userCreateRequest.username());
        newUser.setEmail(userCreateRequest.email());
        newUser.setPasswordHash(passwordEncoder.encode(userCreateRequest.password()));
        newUser.setFirstName(userCreateRequest.firstName());
        newUser.setLastName(userCreateRequest.lastName());
        newUser.setEnabled(true);
        newUser.getRoles().addAll(resolveRoles(userCreateRequest.roles()));

        return UserResponse.from(userRepository.save(newUser));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {

        CrmUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Staleness is checked first: if the caller is looking at an old copy, the
        // email they are "changing to" may already be what the record says. Report
        // the stale view rather than sending them off to fix a duplicate that isn't one.
        if (!Objects.equals(user.getVersion(), request.version())) {
            throw new ConflictException(
                    "This user changed since you opened it — reload and try again");
        }

        // only conflict-check the email if it actually changed — otherwise a
        // no-op edit would 409 the user against their own address
        if (!user.getEmail().equals(request.email())
                && userRepository.existsByEmail(request.email()))
            throw new ConflictException("Email is already in use");

        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(request.enabled());

        // replace roles by MUTATING the managed collection (clear + addAll),
        // not by swapping in a new Set — Hibernate tracks the join-table diff
        user.getRoles().clear();
        user.getRoles().addAll(resolveRoles(request.roles()));

        return UserResponse.from(user); // managed entity → dirty-checking flushes on
    }

    @Transactional
    public void deactivate(Long id, Long currentUserId) {

        if (id.equals(currentUserId))
            throw new ConflictException("You cannot deactivate your own account");

        CrmUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        user.setEnabled(false);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    /** Names → Role entities; unknown name is a client error. */
    private Set<Role> resolveRoles(Set<String> roleNames) {

        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("No such role: " + name)))
                .collect(Collectors.toSet());
    }
}
