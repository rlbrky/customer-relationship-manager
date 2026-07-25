package com.berkay.crm.api;

import com.berkay.crm.dto.LoginRequest;
import com.berkay.crm.dto.UserResponse;
import com.berkay.crm.security.CrmUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SecurityContextRepository securityContextRepository;

    private final AuthenticationManager authenticationManager;

    public AuthController(SecurityContextRepository securityContextRepository,
                          AuthenticationManager authenticationManager) {

        this.securityContextRepository = securityContextRepository;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

        // 1 - authenticate
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()
                )
        );

        // 2 - put authenticated result into context for THIS thread
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // 3 - persist context into HttpSession, so cookie-bearing NEXT request is recognized
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // 4 - return the user response
        CrmUserDetails principal = (CrmUserDetails) auth.getPrincipal();
        return UserResponse.from(principal.getCrmUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {

        // clear everything and return an empty response
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(
            @AuthenticationPrincipal CrmUserDetails principal
            ) {
        return UserResponse.from(principal.getCrmUser());
    }
}
