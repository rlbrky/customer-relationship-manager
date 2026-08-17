package com.berkay.crm.repository;

import com.berkay.crm.model.DealStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DealStageHistoryRepository extends JpaRepository<DealStageHistory, Long> {

    List<DealStageHistory> findByDealIdOrderByChangedAtAsc(Long dealId);
}
