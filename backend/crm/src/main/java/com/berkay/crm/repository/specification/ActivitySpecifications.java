package com.berkay.crm.repository.specification;

import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.security.Roles;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class ActivitySpecifications {

    public static Specification<Activity> inAccount(Long accountId) {

        return (root, query, cb) ->
                cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Activity> ofType(ActivityType type) {

        return (root, query, cb) ->
                cb.equal(root.get("type"), type);
    }

    public static Specification<Activity> isCompleted(boolean isCompleted) {

        return (root, query, cb) ->
                cb.equal(root.get("completed"), isCompleted);
    }

    public static Specification<Activity> visibleTo(CrmUser user) {

        return (root, query, cb) -> {

            if(Roles.isPrivileged(user)) {
                return cb.conjunction(); // always true predicate -> no restriction
            }

            return cb.equal(root.get("account").get("owner").get("id"), user.getId());
        };
    }

    public static Specification<Activity> dueBefore(LocalDateTime now) {

        return (root, query, cb) ->
                cb.lessThan(root.get("dueAt"), now);
    }

    private ActivitySpecifications() {}
}
