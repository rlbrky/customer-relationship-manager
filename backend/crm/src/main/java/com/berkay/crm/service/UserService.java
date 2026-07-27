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
    public UserResponse update(Long id, UserUpdateRequest userUpdateRequest) {

        CrmUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // only conflict-check the email if it actually changed — otherwise a
        // no-op edit would 409 the user against their own address
        if (!user.getEmail().equals(userUpdateRequest.email())
                && userRepository.existsByEmail(userUpdateRequest.email()))
            throw new ConflictException("Email is already in use");

        user.setEmail(userUpdateRequest.email());
        user.setFirstName(userUpdateRequest.firstName());
        user.setLastName(userUpdateRequest.lastName());
        user.setEnabled(userUpdateRequest.enabled());

        // replace roles by MUTATING the managed collection (clear + addAll),
        // not by swapping in a new Set — Hibernate tracks the join-table diff
        user.getRoles().clear();
        user.getRoles().addAll(resolveRoles(userUpdateRequest.roles()));

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
