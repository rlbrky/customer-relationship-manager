package com.berkay.crm.repository;

import com.berkay.crm.model.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Account> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Page<Account> findAllByOwnerId(Long ownerId, Pageable pageable);
}
