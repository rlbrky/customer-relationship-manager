package com.berkay.crm.security;

import com.berkay.crm.model.CrmUser;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.stream.Collectors;

public class CrmUserDetails implements UserDetails {

    private final CrmUser crmUser;

    public CrmUserDetails(CrmUser crmUser) {
        this.crmUser = crmUser;
    }

    @Override
    public String getUsername() {
        return crmUser.getUsername();
    }

    public String getPassword() {
        return crmUser.getPasswordHash();
    }

    public boolean isEnabled() {
        return crmUser.isEnabled();
    }

    public boolean isAccountNonExpired() {
        return true;
    }

    public boolean isAccountNonLocked() {
        return true;
    }

    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {

        return crmUser.getRoles().stream().map(role ->
                        new SimpleGrantedAuthority(role.getName()))
                            .collect(Collectors.toUnmodifiableList());
    }

    public CrmUser getCrmUser() {
        return crmUser;
    }
}
