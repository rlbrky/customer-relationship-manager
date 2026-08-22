package com.berkay.crm.dto;

import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;

import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(Long id, Integer version, String username, String email, String firstName,
                           String lastName, boolean enabled, Set<String> roles) {

    public static UserResponse from(CrmUser user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(), user.getVersion(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), user.isEnabled(), roleNames
        );
    }
}
