package com.berkay.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
    Forces the CsrfToken to be loaded so CookieCsrfTokenRepository writes the XSRF-Token Cookie
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Reading token value triggers repository to render the cookie
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        token.getToken();
        filterChain.doFilter(request, response);
    }
}
