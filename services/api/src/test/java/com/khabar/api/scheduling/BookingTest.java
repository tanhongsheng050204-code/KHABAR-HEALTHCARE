package com.khabar.api.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.khabar.api.support.TestTokens.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B1: patients book their own visit from a rolling calendar of the clinic's open slots. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdjustableClock clock;
    @Autowired ClinicRepository clinics;
    @Autowired AppUserRepository users;
    @Autowired PatientRepository patients;
    @Autowired CaregiverLinkRepository caregiverLinks;

    AppUser doctor, doctorElsewhere, aminahAccount, tanAccount, nurul;
    Patient aminah;

    @BeforeEach
    void setUp() {
        clock.reset();
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya"));
        Clinic other = clinics.save(new Clinic("Klinik Lain"));
        doctor = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Priya", clinic));
        doctorElsewhere = users.save(new AppUser(UUID.randomUUID(), Role.DOCTOR, "Dr Lim", other));
        aminahAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Aminah", null));
        tanAccount = users.save(new AppUser(UUID.randomUUID(), Role.PATIENT, "Tan", null));
        nurul = users.save(new AppUser(UUID.randomUUID(), Role.CAREGIVER, "Nurul", null));
        aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "03-0000 0001", "ms"));
        patients.save(new Patient(clinic, tanAccount, "Tan Kok Hoe", "540101-07-1234", "03-0000 0003", "zh"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }

    List<Instant> slots(AppUser as) throws Exception {
        String body = mvc.perform(get("/api/clinic/slots").header("Authorization", bearer(as.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Instant> slots = new ArrayList<>();
        for (JsonNode slot : json.readTree(body)) {
            slots.add(Instant.parse(slot.get("startsAt").asText()));
        }
        return slots;
    }

    ResultActions book(AppUser as, Instant startsAt) throws Exception {
        return mvc.perform(post("/api/appointments").header("Authorization", bearer(as.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("startsAt", startsAt.toString(), "reason", "Pening sejak 3 hari"))));
    }

    @Test
    void openSlotsAreFifteenMinutesApartInClinicHoursAndNeverInThePast() throws Exception {
        List<Instant> slots = slots(aminahAccount);

        assertThat(slots).isNotEmpty();
        assertThat(slots).allSatisfy(s -> {
            ZonedDateTime local = s.atZone(clock.getZone());
            assertThat(s).isAfter(clock.instant().plus(Duration.ofMinutes(59)));
            assertThat(local.getDayOfWeek()).isNotEqualTo(DayOfWeek.SUNDAY);
            assertThat(local.getMinute() % 15).isZero();
            assertThat(local.toLocalTime()).isBetween(LocalTime.of(9, 0), LocalTime.of(16, 45));
        });
        assertThat(slots.get(slots.size() - 1)).isBefore(clock.instant().plus(Duration.ofDays(14)));
    }

    @Test
    void aPatientBooksASlotAndNobodyElseCanHaveIt() throws Exception {
        Instant first = slots(aminahAccount).get(0);

        book(aminahAccount, first).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startsAt").value(first.toString()));

        assertThat(slots(tanAccount)).doesNotContain(first);
        book(tanAccount, first).andExpect(status().isConflict());
        mvc.perform(get("/api/appointments/mine").header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(jsonPath("$.startsAt").value(first.toString()))
                .andExpect(jsonPath("$.reason").value("Pening sejak 3 hari"));
    }

    @Test
    void onlyRealOpenSlotsCanBeBooked() throws Exception {
        Instant first = slots(aminahAccount).get(0);
        book(aminahAccount, first.plus(Duration.ofMinutes(7))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That is not an open slot. Pick one from the clinic's calendar."));
        book(aminahAccount, clock.instant().minus(Duration.ofDays(1))).andExpect(status().isBadRequest());
    }

    @Test
    void aPatientHoldsOneUpcomingBookingAtATime() throws Exception {
        List<Instant> slots = slots(aminahAccount);
        book(aminahAccount, slots.get(0)).andExpect(status().isCreated());
        book(aminahAccount, slots.get(1)).andExpect(status().isConflict());
    }

    @Test
    void cancellingFreesTheSlot() throws Exception {
        Instant first = slots(aminahAccount).get(0);
        String body = book(aminahAccount, first).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();

        mvc.perform(delete("/api/appointments/{id}", id).header("Authorization", bearer(tanAccount.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/appointments/{id}", id).header("Authorization", bearer(aminahAccount.getId())))
                .andExpect(status().isOk());

        assertThat(slots(tanAccount)).contains(first);
        book(tanAccount, first).andExpect(status().isCreated());
    }

    @Test
    void theDoctorSeesTheDaysBookingsForTheirClinicOnly() throws Exception {
        Instant first = slots(aminahAccount).get(0);
        book(aminahAccount, first);
        String day = first.atZone(clock.getZone()).toLocalDate().toString();

        mvc.perform(get("/api/clinic/appointments").param("date", day).header("Authorization", bearer(doctor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Aminah binti Yusof"))
                .andExpect(jsonPath("$[0].reason").value("Pening sejak 3 hari"));
        mvc.perform(get("/api/clinic/appointments").param("date", day).header("Authorization", bearer(doctorElsewhere.getId())))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyPatientsBookForThemselves() throws Exception {
        Instant first = slots(aminahAccount).get(0);
        book(nurul, first).andExpect(status().isForbidden());
        book(doctor, first).andExpect(status().isForbidden());
    }
}
