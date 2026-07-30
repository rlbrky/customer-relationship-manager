package com.berkay.crm.api;

import com.berkay.crm.dto.ContactResponse;
import com.berkay.crm.dto.ContactUpdateRequest;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("{id}")
    public ContactResponse getContact(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
            )
    {

        return contactService.findById(id, principal.getCrmUser());
    }

    @PutMapping("{id}")
    public ContactResponse updateContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactUpdateRequest request,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        return contactService.update(id, request, principal.getCrmUser());
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteContact(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
    ) {

        contactService.delete(id, principal.getCrmUser());
        return ResponseEntity.noContent().build();
    }
}
