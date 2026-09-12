package com.myhive.backend.model;

/** Where an email address entered the system. Stored as VARCHAR — never reorder or rename. */
public enum ContactSource {
    TRIP_BUILDER,
    VOTE,
    BOOKING,
    CONTACT_FORM,
    PAYMENT
}
