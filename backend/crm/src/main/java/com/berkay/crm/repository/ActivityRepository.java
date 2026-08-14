package com.berkay.crm.repository;

import com.berkay.crm.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityRepository extends JpaRepository<Activity, Long>,
                                                JpaSpecificationExecutor<Activity> {

    @Override
    @EntityGraph(attributePaths = "contact")
    Page<Activity> findAll(Specification<Activity> spec, Pageable pageable);
}
