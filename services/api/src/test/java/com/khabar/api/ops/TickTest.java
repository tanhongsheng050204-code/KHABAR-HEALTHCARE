package com.khabar.api.ops;

import com.khabar.api.service.AgentClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The outside schedule's tick: secret required, and only the jobs this deployment switched on run. */
@SpringBootTest(properties = "khabar.tick.secret=test-tick-secret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TickTest {

    @Autowired MockMvc mvc;
    @MockBean AgentClientService agents;

    @Test
    void aTickNeedsTheSecretAndRunsOnlyEnabledJobs() throws Exception {
        mvc.perform(post("/api/webhooks/tick")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/webhooks/tick").header("X-Khabar-Tick-Secret", "wrong")).andExpect(status().isUnauthorized());
        // The test profile switches every timer off, so the tick runs nothing.
        mvc.perform(post("/api/webhooks/tick").header("X-Khabar-Tick-Secret", "test-tick-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reminders").value("off"))
                .andExpect(jsonPath("$.checkIns").value("off"));
    }
}
