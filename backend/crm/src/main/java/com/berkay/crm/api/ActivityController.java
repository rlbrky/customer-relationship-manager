package com.berkay.crm.api;

import com.berkay.crm.dto.ActivityResponse;
import com.berkay.crm.dto.ActivityUpdateRequest;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("{id}")
    public ActivityResponse getActivity(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return activityService.findById(id, principal.getCrmUser());
    }

    @PutMapping("{id}")
    public ActivityResponse updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody ActivityUpdateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return activityService.update(id, request, principal.getCrmUser());
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteActivity(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        activityService.delete(id, principal.getCrmUser());
        return ResponseEntity.notFound().build();
    }
}
