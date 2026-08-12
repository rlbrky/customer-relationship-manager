package com.berkay.crm.repository.specification;

import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.security.Roles;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class ContactSpecifications {

    private ContactSpecifications() {}

    // Case-insensitive match
    public static Specification<Contact> matches(String value) {

        return (root, query, cb) -> {

            String pattern = "%" + value.toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern));
        };
    }

    public static Specification<Contact> visibleTo(CrmUser user) {

        return (root, query, cb) -> {
            if(Roles.isPrivileged(user)) {
                return cb.conjunction(); // always true predicate -> no restriction
            }

            return cb.equal(root.get("account").get("owner").get("id"), user.getId());
        };
    }

    public static Specification<Contact> inAccount(Long accountId) {

        return (root, query, cb) ->
                cb.equal(root.get("account").get("id"), accountId);
    }
}
