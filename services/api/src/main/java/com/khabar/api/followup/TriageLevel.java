package com.khabar.api.followup;

/** How urgently a patient's reply needs a person. Declared most urgent first. */
public enum TriageLevel {
    RED,
    WATCH,
    REVIEW,
    OK;

    /** Anything the agents service returns that we do not recognise is routed to the clinic review queue. */
    public static TriageLevel fromAgent(Object value) {
        if (value == null) {
            return REVIEW;
        }
        return switch (value.toString().trim().toLowerCase()) {
            case "red" -> RED;
            case "watch" -> WATCH;
            case "ok" -> OK;
            default -> REVIEW;
        };
    }

    public boolean needsACall() {
        return this != OK;
    }
}
