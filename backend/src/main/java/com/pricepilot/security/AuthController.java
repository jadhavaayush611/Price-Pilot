package com.pricepilot.security;

import com.pricepilot.user.UserService;
import com.pricepilot.user.dto.AuthResponseDTO;
import com.pricepilot.user.dto.LoginRequestDTO;
import com.pricepilot.user.dto.UserRequestDTO;
import com.pricepilot.user.dto.UserResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*") // Allows calls from any local frontend client
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, JwtService jwtService,
                          CustomUserDetailsService userDetailsService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody UserRequestDTO requestDTO) {
        UserResponseDTO userResponse = userService.register(requestDTO);
        UserDetails userDetails = userDetailsService.loadUserByUsername(userResponse.getEmail());
        String token = jwtService.generateToken(userDetails, userResponse.getRole().name());

        AuthResponseDTO response = AuthResponseDTO.builder()
                .token(token)
                .user(userResponse)
                .build();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private static final org.slf4j.Logger auditLog = org.slf4j.LoggerFactory.getLogger("AuditLogger");

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requestDTO.getEmail(), requestDTO.getPassword())
            );

            UserResponseDTO userResponse = userService.getUserByEmail(requestDTO.getEmail());
            UserDetails userDetails = userDetailsService.loadUserByUsername(requestDTO.getEmail());

            // Audit log login success
            auditLog.info("AUDIT: LOGIN_SUCCESS | user_email={}", requestDTO.getEmail());

            String token = jwtService.generateToken(userDetails, userResponse.getRole().name());

            AuthResponseDTO response = AuthResponseDTO.builder()
                    .token(token)
                    .user(userResponse)
                    .build();
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Audit log login failure
            auditLog.warn("AUDIT: LOGIN_FAILURE | user_email={} | reason={}", requestDTO.getEmail(), e.getMessage());

            if (e instanceof org.springframework.security.authentication.LockedException) {
                auditLog.warn("AUDIT: ACCOUNT_LOCK | user_email={}", requestDTO.getEmail());
            }
            throw e;
        }
    }
}
