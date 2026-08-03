package com.berkay.crm.repository;

import com.berkay.crm.model.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @EntityGraph(attributePaths = "account")
    Page<Contact> findByAccountId(Long accountId, Pageable pageable);

    long countByAccountId(Long accountId);

    @Query("""
           select c.account.id as accountId, count(c) as total from Contact c
           where c.account.id in :ids
           group by c.account.id
           """)
    List<AccountContactCount> countByAccountIdIn(@Param("ids") Collection<Long> ids);

    /** Projection for the batched query — Spring Data maps aliases to getter names. */
    interface AccountContactCount {
        Long getAccountId();
        long getTotal();
    }
}
