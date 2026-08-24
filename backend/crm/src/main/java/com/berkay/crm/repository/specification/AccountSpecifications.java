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

    /**
     * The whole "which accounts is this person looking at" chain, in one place.
     *
     * Shared by the list endpoint and the CSV export: if the export built its own
     * copy, the day someone adds a filter to the list is the day the export starts
     * writing a different set than the screen shows.
     */
    public static Specification<Account> forFilters(CrmUser user, String name,
                                                    String industry, Long ownerId) {

        // visibility is the SEED of the chain, it can't be skipped
        Specification<Account> spec = visibleTo(user);

        if (name != null && !name.isBlank()) {
            spec = spec.and(nameContains(name));
        }

        if (industry != null && !industry.isBlank()) {
            spec = spec.and(industryIs(industry));
        }

        if (ownerId != null) {
            spec = spec.and(ownedBy(ownerId));
        }

        return spec;
    }
}
