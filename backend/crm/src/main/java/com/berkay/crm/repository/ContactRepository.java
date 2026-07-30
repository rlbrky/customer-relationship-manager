package com.berkay.crm.repository;

import com.berkay.crm.model.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @EntityGraph(attributePaths = "account")
    Page<Contact> findByAccountId(Long accountId, Pageable pageable);
}
