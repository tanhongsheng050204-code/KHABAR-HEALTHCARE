package com.khabar.api.controller;

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

/** Anyone can read what the API offers, and try it with a demo token, without signing in first. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsTest {

    @Autowired MockMvc mvc;

    @Test
    void theEndpointListIsPublicAndCoversTheClinic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Khabar clinical API"))
                .andExpect(jsonPath("$.paths['/api/clinic/call-list']").exists())
                .andExpect(jsonPath("$.paths['/api/patients/{patientId}/previsit']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                // Relative, so "Try it out" calls the same address the page was opened on (https, not http)
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void docsLeadsToTheBrowsablePage() throws Exception {
        mvc.perform(get("/docs")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
