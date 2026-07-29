package com.berkay.crm.api;

import com.berkay.crm.dto.AccountCreateRequest;
import com.berkay.crm.dto.AccountResponse;
import com.berkay.crm.dto.AccountUpdateRequest;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.AccountService;
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
public class AccountsController {

    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public Page<AccountResponse> getAccounts(
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return accountService.findAll(pageable, principal.getCrmUser());
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
}
