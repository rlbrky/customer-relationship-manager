package com.berkay.crm.repository;

import com.berkay.crm.model.CrmUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<CrmUser, Long> {

    public Optional<CrmUser> findByUsername(String username);

    public boolean existsByUsername(String username);

    public boolean existsByEmail(String email);
}
