package com.khabar.api.messaging;

/** Sends a message to a patient's phone. WhatsApp in production; an outbox table locally. */
public interface Messenger {

    /** SAFETY is the fixed emergency advice after a red flag; ANSWER is a doctor-approved answer; NOTICE is an acknowledgement. */
    enum Kind { CHECK_IN, SUMMARY, ANSWER, SAFETY, NOTICE }

    record Result(boolean delivered, String providerId, String error) {
        public static Result ok(String providerId) {
            return new Result(true, providerId, null);
        }

        public static Result failed(String error) {
            return new Result(false, null, error);
        }
    }

    /** Never throws: a failure is reported in the result so a follow-up run can carry on. */
    Result send(String toPhone, String text, String language, Kind kind);

    String channel();
}
