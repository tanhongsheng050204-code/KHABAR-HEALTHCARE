package com.khabar.api.followup;

/** Where a follow-up case stands. Only RESOLVED is closed; UNABLE_TO_CONTACT stays open for another try. */
public enum CaseStatus {
    NEW,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    ESCALATED,
    UNABLE_TO_CONTACT,
    RESOLVED
}
