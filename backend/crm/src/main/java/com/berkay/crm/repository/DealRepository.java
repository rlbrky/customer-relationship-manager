package com.berkay.crm.repository;

import com.berkay.crm.model.Deal;
import com.berkay.crm.model.DealOutcome;
import com.berkay.crm.model.DealStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    @Override
    @EntityGraph(attributePaths = "account")
    Page<Deal> findAll(Specification<Deal> spec, Pageable pageable);

    @Query("""
        select d.stage as stage,
            count(d) as dealCount,
                coalesce(sum(d.value), 0) as totalValue
        from Deal d
            where d.outcome is null
                and (:ownerId is null or d.account.owner.id = :ownerId)
        group by d.stage
    """)
    List<StageTotal> pipelineByStage(@Param("ownerId") Long ownerId);

    @Query("""
        select d.outcome as outcome,
            count(d) as dealCount,
                coalesce(sum(d.value), 0) as totalValue
        from Deal d
            where d.outcome is not null
                and (:ownerId is null or d.account.owner.id = :ownerId)
        group by d.outcome
    """)
    List<OutcomeTotal> closedByOutcome(@Param("ownerId") Long ownerId);


    interface StageTotal {
        DealStage getStage();
        long getDealCount();
        BigDecimal getTotalValue();
    }

    interface OutcomeTotal {
        DealOutcome getOutcome();
        long getDealCount();
        BigDecimal getTotalValue();
    }
}
