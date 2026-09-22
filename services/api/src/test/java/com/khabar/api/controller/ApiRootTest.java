package com.khabar.api.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Someone who opens the API's address in a browser should not see a bare 401. */
class ApiRootTest {

    @Nested
    @SpringBootTest(properties = "khabar.web.app-url=https://khabar.example")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WhenTheAppAddressIsKnown {

        @Autowired MockMvc mvc;

        @Test
        void theRootSendsVisitorsToTheApp() throws Exception {
            mvc.perform(get("/")).andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://khabar.example"));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WhenItIsNot {

        @Autowired MockMvc mvc;

        @Test
        void theRootSaysWhatThisIs() throws Exception {
            mvc.perform(get("/")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.service").value("Khabar clinical API"))
                    .andExpect(jsonPath("$.health").value("/api/health"));
        }
    }
}
