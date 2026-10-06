package com.myhive.backend.dto;

import com.myhive.backend.model.ContactSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
    private UUID id;
    /** Null for a phone-only contact. */
    private String email;
    /** E.164; null for an email-only contact. */
    private String phone;
    private String name;
    private String locale;
    private ContactSource firstSource;
    private ContactSource lastSource;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private int touchCount;
    /** True when the address sits in email_suppressions — do not email this person. */
    private boolean unsubscribed;
}
