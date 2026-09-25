package com.khabar.api.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentClientService;
import com.khabar.api.service.AgentDtos.Normalised;
import com.khabar.api.service.AgentDtos.WrittenAs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API keeps the patient graph in step as things change, and nothing that identifies a patient ever reaches it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientGraphSyncTest {

    static final Neo4j NEO4J = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    static final Driver DRIVER = GraphDatabase.driver(NEO4J.boltURI(), AuthTokens.none());

    @DynamicPropertySource
    static void graph(DynamicPropertyRegistry registry) {
        registry.add("khabar.graph.uri", () -> NEO4J.boltURI().toString());
        registry.add("khabar.graph.password", () -> "unused");
    }

    @AfterAll
    static void stop() {
        DRIVER.close();
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired PatientGraphSync sync;
    @MockBean AgentClientService agents;

    static final String NAME = "Siti Aminah binti Kassim";
    static final String IC = "850101-14-5678";
    static final String PHONE = "012-777 8899";

    AppUser account;
    Patient siti;

    @BeforeEach
    void setUp() {
        try (Session session = DRIVER.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }
        Clinic clinic = clinics.save(new Clinic("Klinik Ujian"));
        users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Test", clinic));
        account = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Siti", null));
        siti = patients.save(new Patient(clinic, account, NAME, IC, PHONE, "ms"));
        when(agents.normalise(anyList(), anyList())).thenReturn(new Normalised(
                Map.of("Brand A 500mg", new WrittenAs("metformin", "brand a")),
                Map.of("Jus peria (bitter gourd)", "bitter gourd")));
    }

    ResultActions add(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/patients/{id}/medications", siti.getId()).header("Authorization", bearer(account.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    List<Record> read(String query) {
        try (Session session = DRIVER.session()) {
            return session.run(query, Map.of("g", siti.getGraphId().toString())).list();
        }
    }

    @Test
    void whatThePatientAddsReachesTheGraphUnderTheirGraphId() throws Exception {
        add(Map.of("name", "Brand A 500mg", "kind", "MEDICINE", "source", "GP clinic")).andExpect(status().isCreated());
        add(Map.of("name", "Jus peria (bitter gourd)", "kind", "HERB", "source", "Family")).andExpect(status().isCreated());

        assertThat(read("MATCH (:Patient {graph_id: $g})-[t:TAKES]->(m:Medication)<-[:BRAND_OF]-(b:Brand) RETURN t.name AS name, m.generic AS generic, b.name AS brand"))
                .singleElement().satisfies(r -> {
                    assertThat(r.get("name").asString()).isEqualTo("Brand A 500mg");
                    assertThat(r.get("generic").asString()).isEqualTo("metformin");
                    assertThat(r.get("brand").asString()).isEqualTo("brand a");
                });
        assertThat(read("MATCH (:Patient {graph_id: $g})-[:USES]->(h:Herb) RETURN h.name AS herb"))
                .extracting(r -> r.get("herb").asString()).containsExactly("bitter gourd");
    }

    @Test
    void stoppingAMedicineTakesItOutOfTheGraph() throws Exception {
        String body = add(Map.of("name", "Brand A 500mg")).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/patients/{id}/medications/{item}", siti.getId(), json.readTree(body).get("id").asText())
                .header("Authorization", bearer(account.getId()))).andExpect(status().isOk());

        assertThat(read("MATCH (:Patient {graph_id: $g})-[t:TAKES]->() RETURN t")).isEmpty();
    }

    @Test
    void aWorryingHomeReadingIsInTheGraph() throws Exception {
        mvc.perform(post("/api/patients/{id}/readings", siti.getId()).header("Authorization", bearer(account.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"glucose\": 3.1}")).andExpect(status().isCreated());

        assertThat(read("MATCH (:Patient {graph_id: $g})-[:RECORDED]->(r:Reading) RETURN r.kind AS kind, r.level AS level"))
                .singleElement().satisfies(r -> assertThat(r.get("kind").asString()).isEqualTo("GLUCOSE"));
    }

    @Test
    void aFollowUpReplyWithAWarningWordIsInTheGraph() throws Exception {
        when(agents.triageReply(anyString(), any())).thenReturn(Map.of("level", "watch", "matched", "pening"));
        mvc.perform(post("/api/followup/replies").header("Authorization", bearer(account.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"Pening sikit\"}")).andExpect(status().isOk());

        assertThat(read("MATCH (:Patient {graph_id: $g})-[r:REPORTED]->(s:Symptom) RETURN s.word AS word, r.level AS level"))
                .singleElement().satisfies(r -> assertThat(r.get("word").asString()).isEqualTo("pening"));
    }

    @Test
    void nothingThatIdentifiesThePatientEverReachesTheGraph() throws Exception {
        add(Map.of("name", "Ubat Siti " + IC, "kind", "MEDICINE", "source", "Kassim's shop, call " + PHONE)).andExpect(status().isCreated());
        add(Map.of("name", "Jamu from Aminah's sister", "kind", "HERB", "source", NAME)).andExpect(status().isCreated());

        List<String> everything = new ArrayList<>();
        read("MATCH (n) RETURN properties(n) AS p UNION ALL MATCH ()-[r]->() RETURN properties(r) AS p")
                .forEach(r -> r.get("p").asMap().values().forEach(v -> everything.add(String.valueOf(v))));
        assertThat(everything).isNotEmpty();
        String icDigits = IC.replaceAll("\\D", "");
        String phoneDigits = PHONE.replaceAll("\\D", "");
        assertThat(everything).noneMatch(v -> v.contains("Siti") || v.contains("Aminah") || v.contains("Kassim")
                || v.replaceAll("\\D", "").contains(icDigits) || v.replaceAll("\\D", "").contains(phoneDigits));
        assertThat(read("MATCH (p:Patient {graph_id: $g}) RETURN keys(p) AS keys").get(0).get("keys").asList())
                .containsExactlyInAnyOrder("graph_id", "pregnant");
    }

    @Test
    void factsThatStillIdentifyThePatientAreRefused() {
        GraphFacts leaky = new GraphFacts(siti.getGraphId(), false, List.of(), List.of("Siti's usual allergy"),
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> PatientGraphSync.requireNoIdentity(leaky, siti)).isInstanceOf(IllegalStateException.class);
        GraphFacts phone = new GraphFacts(siti.getGraphId(), false, List.of(), List.of(), List.of(
                new GraphFacts.Taken("Panadol", "panadol", false, null, "call 012-777 8899", null)), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> PatientGraphSync.requireNoIdentity(phone, siti)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenTheAgentsCannotNameTheDrugItIsKeptAsWrittenAndUnrecognised() throws Exception {
        when(agents.normalise(anyList(), anyList())).thenThrow(new IllegalStateException("agents down"));
        add(Map.of("name", "Brand A 500mg")).andExpect(status().isCreated());

        assertThat(read("MATCH (:Patient {graph_id: $g})-[:TAKES]->(m:Medication) RETURN m.generic AS key, m.recognised AS recognised"))
                .singleElement().satisfies(r -> {
                    assertThat(r.get("key").asString()).isEqualTo("brand a 500mg");
                    assertThat(r.get("recognised").asBoolean()).isFalse();
                });
    }

    @Test
    void theWholeClinicCanBeWrittenAgainAfterTheGraphIsEmptied() {
        assertThat(sync.syncAll()).isGreaterThanOrEqualTo(1);
        assertThat(read("MATCH (p:Patient {graph_id: $g}) RETURN p")).hasSize(1);
    }
}
