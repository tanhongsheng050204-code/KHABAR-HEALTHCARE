package com.khabar.api.scheduling;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * When a clinic can be booked: 15-minute slots, weekdays 9–12 and 2–5, Saturday 9–12, closed on
 * Sunday, from an hour from now up to two weeks ahead. The same for every clinic for now; public
 * holidays are not known yet.
 */
public final class ClinicHours {

    static final Duration SLOT = Duration.ofMinutes(15);
    static final Duration LEAD_TIME = Duration.ofHours(1);
    static final Duration WINDOW = Duration.ofDays(14);

    private static final List<LocalTime[]> WEEKDAY = List.of(
            new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(12, 0)},
            new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(17, 0)});
    private static final List<LocalTime[]> SATURDAY = List.<LocalTime[]>of(new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(12, 0)});

    private ClinicHours() {
    }

    /** Every slot start in the booking window, whether booked or not. */
    public static List<Instant> slots(Instant now, ZoneId zone) {
        Instant earliest = now.plus(LEAD_TIME);
        Instant latest = now.plus(WINDOW);
        LocalDate today = now.atZone(zone).toLocalDate();
        List<Instant> slots = new ArrayList<>();
        for (int d = 0; d <= WINDOW.toDays(); d++) {
            LocalDate date = today.plusDays(d);
            for (LocalTime[] session : sessions(date.getDayOfWeek())) {
                for (LocalTime t = session[0]; !t.plus(SLOT).isAfter(session[1]); t = t.plus(SLOT)) {
                    Instant start = ZonedDateTime.of(date, t, zone).toInstant();
                    if (start.isAfter(earliest) && start.isBefore(latest)) {
                        slots.add(start);
                    }
                }
            }
        }
        return slots;
    }

    private static List<LocalTime[]> sessions(DayOfWeek day) {
        return switch (day) {
            case SUNDAY -> List.of();
            case SATURDAY -> SATURDAY;
            default -> WEEKDAY;
        };
    }
}
