package com.berkay.crm.api;

import com.berkay.crm.dto.*;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.AccountService;
import com.berkay.crm.service.ActivityService;
import com.berkay.crm.service.ContactService;
import com.berkay.crm.service.DealService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final ContactService contactService;
    private final ActivityService activityService;
    private final DealService dealService;

    public AccountController(AccountService accountService, ContactService contactService,
                             ActivityService activityService, DealService dealService) {
        this.accountService = accountService;
        this.contactService = contactService;
        this.activityService = activityService;
        this.dealService = dealService;
    }

    @GetMapping
    public Page<AccountResponse> getAccounts(
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long ownerId
            ) {

        return accountService.findAll(
                pageable,
                principal.getCrmUser(),
                name,
                industry,
                ownerId
                );
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        return accountService.findById(id, principal.getCrmUser());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountCreateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        AccountResponse createdAccount = accountService.create(request, principal.getCrmUser());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(createdAccount.id()).toUri();

        return ResponseEntity.created(location).body(createdAccount); // 201 + location
    }

    @PutMapping("/{id}")
    public AccountResponse updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountUpdateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        return accountService.update(id, request, principal.getCrmUser());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        accountService.delete(id, principal.getCrmUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/contacts")
    public Page<ContactResponse> getContacts(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal,
            @RequestParam(required = false) String q
    ) {

        return contactService.findByAccount(accountId, pageable, principal.getCrmUser(), q);
    }

    @PostMapping("/{accountId}/contacts")
    public ResponseEntity<ContactResponse> createContact(
            @Valid @RequestBody ContactCreateRequest request,
            @PathVariable Long accountId,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        ContactResponse createdContact = contactService.create(accountId, request, principal.getCrmUser());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/contacts/{id}").buildAndExpand(createdContact.id()).toUri();

        return ResponseEntity.created(location).body(createdContact); // 201 + location
    }

    @GetMapping("/{accountId}/activities")
    public Page<ActivityResponse> getActivities(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal,
            ActivityType type
    ) {

        return activityService.findByAccount(accountId, pageable, principal.getCrmUser(), type);
    }

    @PostMapping("/{accountId}/activities")
    public ResponseEntity<ActivityResponse> createActivities(
            @Valid @RequestBody ActivityCreateRequest request,
            @PathVariable Long accountId,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        ActivityResponse createdActivity = activityService.create(accountId, request, principal.getCrmUser());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/activities/{id}").buildAndExpand(createdActivity.id()).toUri();

        return ResponseEntity.created(location).body(createdActivity);
    }

    @GetMapping("/{accountId}/deals")
    public Page<DealResponse> getDeals(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "expectedCloseDate") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        return dealService.findByAccount(accountId, pageable, principal.getCrmUser());
    }

    @PostMapping("/{accountId}/deals")
    public ResponseEntity<DealResponse> createDeal(
            @PathVariable Long accountId,
            @Valid @RequestBody DealCreateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        DealResponse createdDeal = dealService.create(accountId, request, principal.getCrmUser());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/deals/{id}").buildAndExpand(createdDeal.id()).toUri();

        return ResponseEntity.created(location).body(createdDeal);
    }
}
