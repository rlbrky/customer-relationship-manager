package com.berkay.crm.api;

import com.berkay.crm.dto.*;
import com.berkay.crm.model.DealStage;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.DealService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @PutMapping("/{id}")
    public DealResponse updateDeal(
            @PathVariable Long id,
            @Valid @RequestBody DealUpdateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return dealService.update(id, request, principal.getCrmUser());
    }

    @PatchMapping("/{id}/stage")
    public DealResponse updateDealStage(
            @PathVariable Long id,
            @Valid @RequestBody DealStageChangeRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return dealService.changeStage(id, request.stage(), principal.getCrmUser());
    }

    @PatchMapping("/{id}/outcome")
    public DealResponse updateDealOutcome(
            @PathVariable Long id,
            @RequestBody DealOutcomeRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return dealService.setOutcome(id, request.outcome(), principal.getCrmUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDeal(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        dealService.delete(id, principal.getCrmUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<DealResponse> getAllDeals(
            @PageableDefault(size = 20, sort = "expectedCloseDate") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal,
            @RequestParam(required = false) DealStage stage,
            @RequestParam(required = false) Boolean open
    ) {

        return dealService.findAll(pageable, principal.getCrmUser(), stage, open);
    }

    @GetMapping("/{id}")
    public DealResponse getDeal(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal) {

        return dealService.findById(id, principal.getCrmUser());
    }

    @GetMapping("/{id}/history")
    public List<DealStageHistoryResponse> getHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        return dealService.findHistory(id, principal.getCrmUser());
    }
}
