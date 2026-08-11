package com.berkay.crm.repository.specification;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.security.Roles;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class AccountSpecifications {

    /*
    * root is the entity being queried (similar to "the FROM clause"),
    * cb is the CriteriaBuilder that manufactures predicates
    * query is the whole query object - don't need it for now
    * */


    // Authorization filter expressed as SQL.
    // Privileged users get an always-true predicate; everyone else is restricted to their own accounts.
    public static Specification<Account> visibleTo(CrmUser user) {

        return (root, query, cb) -> {
            if(Roles.isPrivileged(user)) {
                return cb.conjunction(); // always true predicate -> no restriction
            }

            // root.get("owner").get("id") -> navigates to foreign key without a join, owner_id is already a column in account
            return cb.equal(root.get("owner").get("id"), user.getId());
        };
    }

    public static Specification<Account> nameContains(String value) {

        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + value.toLowerCase() + "%");
    }

    public static Specification<Account> industryIs(String value) {

        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("industry")), value.toLowerCase());
    }

    public static Specification<Account> ownedBy(Long id) {

        return (root, query, cb) ->
                cb.equal(root.get("owner").get("id"), id);
    }

    private AccountSpecifications() {}
}
