package com.berkay.crm.api;

import com.berkay.crm.dto.DeletedAccountResponse;
import com.berkay.crm.service.RecycleBinService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Its own namespace rather than /api/accounts/deleted, which would collide with
 * /api/accounts/{id}. Spring resolves the literal segment first, so it would work
 * — but an endpoint whose correctness rests on pattern precedence is a trap for
 * whoever adds /api/accounts/{slug} later.
 *
 * hasRole('ADMIN') matches the authority ROLE_ADMIN; the prefix is added for you.
 * Writing hasRole('ROLE_ADMIN') looks for ROLE_ROLE_ADMIN and denies everyone.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final RecycleBinService recycleBinService;

    public AdminController(RecycleBinService recycleBinService) {
        this.recycleBinService = recycleBinService;
    }

    @GetMapping("/deleted-accounts")
    public List<DeletedAccountResponse> deletedAccounts() {

        return recycleBinService.deletedAccounts();
    }

    /**
     * POST rather than PUT: this is an action on a resource, not a replacement of
     * its representation, and the client sends no body.
     *
     * 204 rather than the restored account: the thing is no longer deleted, so a
     * DeletedAccountResponse would be a lie, and the caller's next move is to
     * refresh a list that no longer contains it.
     */
    @PostMapping("/deleted-accounts/{id}/restore")
    public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {

        recycleBinService.restore(id);
        return ResponseEntity.noContent().build();
    }
}
