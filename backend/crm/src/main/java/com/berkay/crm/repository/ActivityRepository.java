package com.berkay.crm.repository;

import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long>,
                                                JpaSpecificationExecutor<Activity> {

    @Override
    @EntityGraph(attributePaths = "contact")
    Page<Activity> findAll(Specification<Activity> spec, Pageable pageable);

    @Query("""
       select a.type as type, count(a) as total
       from Activity a
       where (:ownerId is null or a.account.owner.id = :ownerId)
       group by a.type
    """)
    List<TypeTotal> activityMix(@Param("ownerId") Long ownerId);

    interface TypeTotal {
        ActivityType getType();
        long getTotal();
    }
}
