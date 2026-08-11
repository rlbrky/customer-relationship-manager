package com.berkay.crm.security;

import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;

public final class Roles {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String MANAGER = "ROLE_MANAGER";
    public static final String SALES_REP = "ROLE_SALES_REP";

    public static boolean isPrivileged(CrmUser user) {

        return user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(name -> name.equals(ADMIN) || name.equals(MANAGER));
    }

    private Roles() {}
}
