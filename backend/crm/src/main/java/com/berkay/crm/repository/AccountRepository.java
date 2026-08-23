package com.berkay.crm.repository;

import com.berkay.crm.model.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AccountRepository extends
        JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    // Alias for every column, we use no @SQLRestriction on either table so crm_user join is unprotected.
    // IF users ever gain a soft delete this will return rows it shouldn't silently.
    @Query(value = """
            select a.id         as id,
                   a.name       as name,
                   a.industry   as industry,
                   a.deleted_at as deletedAt,
                   u.username   as ownerName
            from account a
            join crm_user u on u.id = a.owner_id
            where a.deleted_at is not null
            order by a.deleted_at desc    
            """, nativeQuery = true)
    List<DeletedAccountRow> findDeleted();

    // revtype = 0 = ADD, 1 = MOD, 2 = DEL - this looks for deleted ones so we type 2
    @Query(value = """
            select a.id as id, r.username as deletedBy
            from account_aud a
            join revinfo r on r.rev = a.rev
            where a.id in (:ids) and a.revtype = 2
            order by a.rev
            """, nativeQuery = true)
    List<DeletedByRow> findDeletedBy(@Param("ids") Collection<Long> ids);

    /** Native projections bind by COLUMN LABEL, which is why every column is aliased. */
    interface DeletedAccountRow {
        Long getId();
        String getName();
        String getIndustry();
        Instant getDeletedAt();
        String getOwnerName();
    }

    interface DeletedByRow {
        Long getId();
        String getDeletedBy();
    }
}
