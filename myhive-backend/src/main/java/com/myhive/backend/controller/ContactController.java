package com.myhive.backend.controller;

import com.myhive.backend.dto.ContactRequest;
import com.myhive.backend.model.ContactSource;
import com.myhive.backend.service.ContactService;
import com.myhive.backend.service.EmailService;
import com.myhive.backend.service.TurnstileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;
    private final TurnstileService turnstileService;
    private final ContactService contactService;

    @PostMapping
    public ResponseEntity<Map<String, String>> submitContactForm(@Valid @RequestBody ContactRequest request) {
        if (!turnstileService.verifyToken(request.getTurnstileToken())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Captcha verification failed"));
        }
        emailService.sendContactNotification(request);
        contactService.touch(request.getEmail(), ContactSource.CONTACT_FORM, request.getName(), null);
        return ResponseEntity.ok(Map.of("message", "Message sent successfully"));
    }
}
