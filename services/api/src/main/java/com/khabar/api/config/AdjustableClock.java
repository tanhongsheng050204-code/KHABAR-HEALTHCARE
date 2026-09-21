package com.khabar.api.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one clock the app reads "now" from. It runs in real time, but a demo (the `local` profile's
 * /dev/clock endpoints) or a test can move it forward, so a 30-day follow-up can be shown in minutes.
 */
public class AdjustableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    public AdjustableClock(Clock base) {
        this.base = base;
    }

    public void advance(Duration by) {
        offset.updateAndGet(current -> current.plus(by));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration offset() {
        return offset.get();
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        AdjustableClock copy = new AdjustableClock(base.withZone(zone));
        copy.offset.set(offset.get());
        return copy;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
