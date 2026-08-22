package com.berkay.crm.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUsername {

    public static final String SYSTEM = "system-user";

    private CurrentUsername() {}

    public static String get() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {

            return SYSTEM;
        }

        return auth.getName();
    }
}
