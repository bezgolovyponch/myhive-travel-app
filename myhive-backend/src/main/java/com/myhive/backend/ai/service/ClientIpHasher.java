package com.myhive.backend.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Turns a caller's IP into the opaque key stored in {@code ai_sessions.client_ip_hash}. The salt
 * keeps the (tiny) IPv4 space from being brute-forced out of a database dump, so the planner can
 * rate-limit a network without keeping anyone's address.
 */
@Component
public class ClientIpHasher {

    private static final String ALGORITHM = "SHA-256";

    private final String salt;

    public ClientIpHasher(@Value("${app.ai.ip-salt:trivlu-ai}") String salt) {
        this.salt = salt;
    }

    public String hash(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] bytes = digest.digest((salt + "|" + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JRE spec; if it is gone the JVM is broken, not the request.
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }
}
