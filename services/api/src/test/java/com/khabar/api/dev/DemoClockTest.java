package com.khabar.api.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo clock lets a presenter jump ahead in the 30-day follow-up. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DemoClockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String doctorToken() throws Exception {
        String body = mvc.perform(post("/dev/token").param("as", "doctor")).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void fastForwardingMovesEveryPatientsFollowUpDay() throws Exception {
        String doctor = doctorToken();
        mvc.perform(get("/api/clinic/call-list").header("Authorization", doctor))
                .andExpect(jsonPath("$.items[?(@.fullName == 'Aminah binti Yusof')].followUpDay").value(3));

        mvc.perform(post("/dev/clock/advance").param("days", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offsetDays").value(4));

        mvc.perform(get("/api/clinic/call-list").header("Authorization", doctor))
                .andExpect(jsonPath("$.items[?(@.fullName == 'Aminah binti Yusof')].followUpDay").value(7));
    }

    @Test
    void theClockCanBeResetToRealTime() throws Exception {
        mvc.perform(post("/dev/clock/advance").param("days", "2"));
        mvc.perform(post("/dev/clock/reset")).andExpect(status().isOk()).andExpect(jsonPath("$.offsetDays").value(0));
    }
}
