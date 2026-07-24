package com.berkay.crm.repository;

import com.berkay.crm.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    public List<Contact> findByAccountId(Long accountId);
}
