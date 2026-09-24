package com.khabar.api.followup;

/** Why a case was closed. Structured, so closures can be counted and reviewed; a note adds the detail. */
public enum ClosureReason {
    CONTACTED_NO_FURTHER_ACTION,
    ADVICE_GIVEN_PER_PROTOCOL,
    APPOINTMENT_ARRANGED,
    DOCTOR_REVIEWED,
    REFERRED_TO_EMERGENCY_CARE,
    UNABLE_TO_CONTACT_AFTER_ATTEMPTS,
    DUPLICATE_OR_FALSE_ALARM,
    /** Only from the older one-step "record contact" call; new screens always choose a reason. */
    CONTACT_RECORDED
}
