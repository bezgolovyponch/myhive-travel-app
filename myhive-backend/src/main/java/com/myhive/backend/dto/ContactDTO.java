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
    private String email;
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
