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

import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * Activities per calendar day since {@code since}.
     *
     * The cast collapses an absolute moment into a calendar day, which is a local
     * concept — so the bucket boundary follows however occurredAt is stored, not
     * the viewer's midnight. Acceptable here; per-viewer bucketing is a feature.
     */
    @Query("""
           select cast(a.occurredAt as LocalDate) as day,
                  count(a) as total
           from Activity a
           where a.occurredAt >= :since
             and (:ownerId is null or a.account.owner.id = :ownerId)
           group by cast(a.occurredAt as LocalDate)
           """)
    List<DailyTotal> activityByDay(@Param("since") Instant since, @Param("ownerId") Long ownerId);

    interface TypeTotal {
        ActivityType getType();
        long getTotal();
    }

    interface DailyTotal {
        LocalDate getDay();
        long getTotal();
    }
}
