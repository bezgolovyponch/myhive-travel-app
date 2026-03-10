package com.myhive.backend.controller;

import com.myhive.backend.dto.AuthRequest;
import com.myhive.backend.dto.AuthResponse;
import com.myhive.backend.dto.AuthValidationResponse;
import com.myhive.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            // Generate JWT token for authenticated user
            String token = jwtUtil.generateToken(request.email(), "ADMIN");
            log.info("Admin logged in: {}", request.email());

            return ResponseEntity.ok(new AuthResponse(token));

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for: {}", request.email());
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<AuthValidationResponse> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String email = jwtUtil.extractEmail(token);

            if (jwtUtil.validateToken(token, email)) {
                return ResponseEntity.ok(new AuthValidationResponse(true, email, "ADMIN"));
            }
        }
        return ResponseEntity.ok(new AuthValidationResponse(false, null, null));
    }

}

