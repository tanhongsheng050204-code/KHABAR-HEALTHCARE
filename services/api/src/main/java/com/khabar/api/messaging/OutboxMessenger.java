package com.khabar.api.messaging;

/**
 * Used when WhatsApp is not configured (local runs, tests): nothing leaves the machine; every
 * message is only recorded in the outbound_message table, where /dev/outbox shows it.
 */
public class OutboxMessenger implements Messenger {

    @Override
    public Result send(String toPhone, String text, String language, Kind kind) {
        return Result.ok(null);
    }

    @Override
    public String channel() {
        return "outbox";
    }
}
