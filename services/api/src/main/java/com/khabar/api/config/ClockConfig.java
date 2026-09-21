package com.khabar.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Malaysian clinics: "today" and check-in times follow Malaysia time unless configured otherwise. */
    @Bean
    public AdjustableClock clock(@Value("${khabar.time-zone:Asia/Kuala_Lumpur}") String zone) {
        return new AdjustableClock(Clock.system(ZoneId.of(zone)));
    }
}
