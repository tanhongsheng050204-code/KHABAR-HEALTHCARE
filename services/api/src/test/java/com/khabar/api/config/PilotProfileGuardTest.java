package com.khabar.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PilotProfileGuardTest {

    @Test
    void pilotCanRunWithoutDevelopmentProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("pilot");

        assertThatCode(() -> PilotProfileGuard.validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void pilotRefusesToRunAlongsideLocalOrDemoProfiles() {
        for (String profile : new String[]{"local", "demo"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles("pilot", profile);
            assertThatThrownBy(() -> PilotProfileGuard.validate(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be combined");
        }
    }
}
