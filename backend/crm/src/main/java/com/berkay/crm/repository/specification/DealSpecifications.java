package com.berkay.crm.repository.specification;

import com.berkay.crm.model.*;
import com.berkay.crm.security.Roles;
import org.springframework.data.jpa.domain.Specification;

public final class DealSpecifications {

    private DealSpecifications() {}

    public static Specification<Deal> inAccount(Long accountId) {

        return (root, query, cb) ->
                cb.equal(root.get("account").get("id"), accountId);
    }

    public static Specification<Deal> visibleTo(CrmUser user) {

        return (root, query, cb) -> {
            if(Roles.isPrivileged(user)) {
                return cb.conjunction(); // always true predicate -> no restriction
            }

            return cb.equal(root.get("account").get("owner").get("id"), user.getId());
        };
    }

    public static Specification<Deal> ofStage(DealStage stage) {

        return (root, query, cb) ->
                cb.equal(root.get("stage"), stage);
    }

    public static Specification<Deal> isOpen(boolean isOpen) {
        return (root, query, cb) ->
                isOpen
                ? cb.isNull(root.get("outcome"))
                : cb.isNotNull(root.get("outcome"));
    }
}
