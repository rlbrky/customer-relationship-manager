package com.berkay.crm.repository.specification;

import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import org.springframework.data.jpa.domain.Specification;

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

    private ActivitySpecifications() {}
}
