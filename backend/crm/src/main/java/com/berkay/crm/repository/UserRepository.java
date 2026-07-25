package com.berkay.crm.repository;

import com.berkay.crm.model.CrmUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<CrmUser, Long> {

    public Optional<CrmUser> findByUsername(String username);

    public boolean existsByUsername(String username);

    public boolean existsByEmail(String email);

    @Query("select u from CrmUser u left join fetch u.roles where u.username = :username")
    public Optional<CrmUser> findByUsernameWithRoles(@Param("username") String username);
}
