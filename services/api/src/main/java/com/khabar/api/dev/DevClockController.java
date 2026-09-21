package com.khabar.api.dev;

import com.khabar.api.config.AdjustableClock;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;

/** `local` profile only: fast-forward the app's clock to demo the 30-day follow-up. */
@RestController
@RequestMapping("/dev/clock")
@Profile("local")
public class DevClockController {

    private final AdjustableClock clock;

    public DevClockController(AdjustableClock clock) {
        this.clock = clock;
    }

    public record ClockState(LocalDate today, long offsetDays) {
    }

    @GetMapping
    public ClockState now() {
        return new ClockState(LocalDate.now(clock), clock.offset().toDays());
    }

    @PostMapping("/advance")
    public ClockState advance(@RequestParam(defaultValue = "1") int days) {
        clock.advance(Duration.ofDays(days));
        return now();
    }

    @PostMapping("/reset")
    public ClockState reset() {
        clock.reset();
        return now();
    }
}
