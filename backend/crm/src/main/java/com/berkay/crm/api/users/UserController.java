package com.berkay.crm.api.users;

import com.berkay.crm.dto.UserCreateRequest;
import com.berkay.crm.dto.UserResponse;
import com.berkay.crm.dto.UserUpdateRequest;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<UserResponse> getUsers(
            Pageable pageable
    ) {

       return userService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {

        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request
            ) {

        UserResponse createdUser = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(createdUser.id()).toUri();

        return ResponseEntity.created(location).body(createdUser); // 201 + location
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(
            @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request
    ) {

        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal CrmUserDetails principal
            ) {

        userService.deactivate(id, principal.getCrmUser().getId()); // lockout guard needs the caller id

        return ResponseEntity.noContent().build(); // 204
    }
}
