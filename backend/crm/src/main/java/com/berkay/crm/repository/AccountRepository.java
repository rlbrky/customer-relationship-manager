package com.berkay.crm.repository;

import com.berkay.crm.model.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountRepository extends
        JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);
}
