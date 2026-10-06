package com.myhive.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Adds the organiser's other contact to a running vote; at least one field must be set. */
@Getter
@Setter
public class VoteSessionContactRequest {
    @Email private String initiatorEmail;
    /** E.164 with the country code ("+447700900123"). */
    @Size(max = 32) private String initiatorPhone;
}
