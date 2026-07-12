package com.pricepilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load UserDetails from database on every request to check active/enabled/non-locked status
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails) && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    org.slf4j.Logger auditLog = org.slf4j.LoggerFactory.getLogger("AuditLogger");
                    if (!userDetails.isEnabled()) {
                        auditLog.warn("AUDIT: JWT_REJECTED | user_email={} | reason=disabled_user", userEmail);
                    } else if (!userDetails.isAccountNonLocked()) {
                        auditLog.warn("AUDIT: ACCOUNT_LOCK | user_email={} | reason=locked_user", userEmail);
                    } else {
                        auditLog.warn("AUDIT: JWT_REJECTED | user_email={} | reason=invalid_or_expired_token", userEmail);
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.Logger auditLog = org.slf4j.LoggerFactory.getLogger("AuditLogger");
            auditLog.warn("AUDIT: JWT_AUTHENTICATION_FAILURE | reason={}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
