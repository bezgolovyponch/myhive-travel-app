package com.myhive.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class UserInfoController {

    @Value("${app.auth.roles-claim}")
    private String rolesClaim;

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(rolesClaim);
        return Map.of(
                "email", jwt.getClaimAsString("email"),
                "roles", roles != null ? roles : List.of(),
                "sub", jwt.getSubject()
        );
    }
}
