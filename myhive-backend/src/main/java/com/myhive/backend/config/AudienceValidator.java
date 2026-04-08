package com.myhive.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error AUDIENCE_ERROR = new OAuth2Error("invalid_token", "Required audience not found", null);

    private final String audience;

    public AudienceValidator(@Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(AUDIENCE_ERROR);
    }
}
