package com.khabar.api.dev;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.labs.LabResult;
import com.khabar.api.labs.LabResultRepository;
import com.khabar.api.pathways.PatientPathway;
import com.khabar.api.pathways.PatientPathwayRepository;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import com.khabar.api.reminders.AppointmentOutcome;
import com.khabar.api.reminders.AppointmentOutcomeRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import com.khabar.api.scheduling.Appointment;
import com.khabar.api.scheduling.AppointmentRepository;
import com.khabar.api.teachback.TeachBack;
import com.khabar.api.teachback.TeachBackRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Thirty days of made-up history for the demo clinic, so trends, the risk score and the reports have something to
 * show: Aminah's own readings, doses and HbA1c, plus ten more fictional patients who are improving, slipping, missing
 * doses or a visit. Phone numbers use 03-0000 01xx, which no real line has. Adds only what is missing.
 */
@Component
@Profile("local")
public class DemoHistory {

    /** sugar: start and end of the 30-day line (0 = none); bp: start and end systolic (0 = none); taken: share of doses taken. */
    record Story(String name, String ic, String phone, String language, List<String> pathways, double sugarFrom, double sugarTo,
                 int bpFrom, int bpTo, double taken, double[] hba1c, int teachBackRight, boolean noShow, boolean reviewDue) {
    }

    static final List<Story> STORIES = List.of(
            new Story("Lim Mei Ling", "580214-10-6102", "03-0000 0101", "zh", List.of(Pathways.DIABETES), 10.2, 7.1, 0, 0, 0.92, new double[]{8.9, 7.6}, 3, false, false),
            new Story("Ahmad bin Ismail", "600921-08-5513", "03-0000 0102", "ms", List.of(Pathways.DIABETES, Pathways.HYPERTENSION), 7.4, 10.8, 138, 152, 0.55, new double[]{7.4, 8.6}, -1, false, false),
            new Story("Kavitha a/p Subramaniam", "650330-07-6224", "03-0000 0103", "ta", List.of(Pathways.HYPERTENSION), 0, 0, 156, 134, 0.9, new double[0], 3, false, false),
            new Story("Wong Ah Kow", "520707-05-5035", "03-0000 0104", "zh", List.of(Pathways.HYPERTENSION), 0, 0, 136, 158, 0.6, new double[0], -1, false, false),
            new Story("Siti Hajar binti Osman", "700112-01-6046", "03-0000 0105", "ms", List.of(Pathways.DIABETES), 7.3, 7.2, 0, 0, 0.85, new double[]{7.3}, 1, false, false),
            new Story("Ravi a/l Krishnan", "590505-14-5157", "03-0000 0106", "ta", List.of(Pathways.DIABETES), 8.8, 9.6, 0, 0, 0.7, new double[]{9.2}, -1, true, false),
            new Story("Nor Azlina binti Hassan", "780919-03-6268", "03-0000 0107", "ms", List.of(Pathways.POST_OP), 0, 0, 0, 0, 0.95, new double[0], 2, false, true),
            new Story("Chong Wei Jie", "630303-10-5379", "03-0000 0108", "en", List.of(Pathways.DIABETES), 9.1, 6.8, 0, 0, 0.95, new double[]{8.5, 7.2}, 3, false, false),
            new Story("Fatimah binti Abdullah", "551225-11-6480", "03-0000 0109", "ms", List.of(Pathways.DIABETES, Pathways.HYPERTENSION), 7.0, 7.0, 132, 131, 0.97, new double[]{6.9}, 3, false, false),
            new Story("Ganesh a/l Muniandy", "610616-08-5591", "03-0000 0110", "ta", List.of(Pathways.DIABETES), 7.6, 8.9, 0, 0, 0.75, new double[]{7.9}, -1, false, false));

    private static final Map<String, String> MEDICINES = Map.of(
            Pathways.DIABETES, "• Metformin 500 mg — 1, after food",
            Pathways.HYPERTENSION, "• Amlodipine 5 mg — 1",
            Pathways.POST_OP, "• Paracetamol 1 g — 1, after food");

    private final AppUserRepository users;
    private final PatientRepository patients;
    private final PatientPathwayRepository pathways;
    private final ReadingRepository readings;
    private final DoseLogRepository doses;
    private final LabResultRepository labs;
    private final TeachBackRepository teachBacks;
    private final AppointmentRepository appointments;
    private final AppointmentOutcomeRepository outcomes;
    private final AdjustableClock clock;

    public DemoHistory(AppUserRepository users, PatientRepository patients, PatientPathwayRepository pathways, ReadingRepository readings,
                       DoseLogRepository doses, LabResultRepository labs, TeachBackRepository teachBacks, AppointmentRepository appointments,
                       AppointmentOutcomeRepository outcomes, AdjustableClock clock) {
        this.users = users;
        this.patients = patients;
        this.pathways = pathways;
        this.readings = readings;
        this.doses = doses;
        this.labs = labs;
        this.teachBacks = teachBacks;
        this.appointments = appointments;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    /** Called by DemoData at start-up and after the demo reset, which removes every home reading in the clinic. */
    @Transactional
    public void seed() {
        AppUser doctor = users.findById(DemoData.DOCTOR_ID).orElse(null);
        if (doctor == null || doctor.getClinic() == null) {
            return;
        }
        Clinic clinic = doctor.getClinic();
        Map<String, Patient> byPhone = new java.util.HashMap<>();
        patients.findByClinicId(clinic.getId()).forEach(p -> byPhone.putIfAbsent(p.getPhone(), p));

        Patient aminah = byPhone.get("03-0000 0001");
        if (aminah != null) {
            history(aminah, doctor, new Story("Aminah binti Yusof", null, null, "ms", List.of(Pathways.DIABETES, Pathways.HYPERTENSION),
                    7.6, 9.4, 142, 146, 0.8, new double[]{7.8, 8.1}, -1, false, false), 0);
        }
        for (int i = 0; i < STORIES.size(); i++) {
            Story story = STORIES.get(i);
            Patient patient = byPhone.get(story.phone());
            if (patient == null) {
                patient = new Patient(clinic, null, story.name(), story.ic(), story.phone(), story.language());
                patient.startFollowUp(LocalDate.now(clock).minusDays(30));
                patient = patients.save(patient);
            }
            history(patient, doctor, story, i + 1);
        }
    }

    private void history(Patient patient, AppUser doctor, Story story, int seed) {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Random random = new Random(2026L * 31 + seed);

        if (pathways.findByPatientIdAndEndedAtIsNullOrderByStartedAt(patient.getId()).isEmpty()) {
            for (String key : story.pathways()) {
                Pathways.Definition d = Pathways.DEFINITIONS.get(key);
                LocalDate next = story.reviewDue() ? today.minusDays(1) : today.plusWeeks(d.reviewEveryWeeks()).minusDays(30);
                pathways.save(new PatientPathway(patient.getId(), patient.getClinic().getId(), key, d.defaults(), d.reviewEveryWeeks(),
                        next, doctor.getId(), now.minus(Duration.ofDays(30))));
            }
        }

        boolean hasHistory = readings.findByPatientIdOrderByMeasuredAtAsc(patient.getId()).stream()
                .anyMatch(r -> r.getMeasuredAt().isBefore(now.minus(Duration.ofDays(7))));
        if (!hasHistory) {
            for (int day = 30; day >= 1; day--) {
                double progress = (30 - day) / 29.0;
                Instant morning = today.minusDays(day).atTime(LocalTime.of(7, 30)).atZone(zone).toInstant();
                if (story.sugarFrom() > 0 && day % 2 == 0) {
                    double mmol = Math.round((story.sugarFrom() + (story.sugarTo() - story.sugarFrom()) * progress + random.nextGaussian() * 0.4) * 10) / 10.0;
                    readings.save(handled(Reading.glucose(patient, Math.max(3.5, mmol), morning, morning, "patient"), doctor, morning));
                }
                if (story.bpFrom() > 0 && day % 3 == 0) {
                    int sys = (int) Math.round(story.bpFrom() + (story.bpTo() - story.bpFrom()) * progress + random.nextGaussian() * 4);
                    int dia = (int) Math.round(sys * 0.6 + random.nextGaussian() * 3);
                    Instant evening = morning.plus(Duration.ofHours(12));
                    readings.save(handled(Reading.bloodPressure(patient, sys, dia, evening, evening, "patient"), doctor, evening));
                }
            }
        }

        UUID plan = UUID.nameUUIDFromBytes(("demo-plan:" + patient.getId()).getBytes(StandardCharsets.UTF_8));
        String medicines = String.join("\n", story.pathways().stream().map(MEDICINES::get).toList());
        for (int day = 30; day >= 1; day--) {
            LocalDate date = today.minusDays(day);
            for (String slot : List.of("morning", "night")) {
                if (doses.existsByPatientIdAndDayAndSlot(patient.getId(), date, slot)) {
                    continue;
                }
                Instant sent = date.atTime(slot.equals("morning") ? LocalTime.of(8, 0) : LocalTime.of(21, 0)).atZone(zone).toInstant();
                DoseLog log = new DoseLog(patient.getId(), plan, date, slot, medicines, sent);
                log.delivered(true);
                // Adherence drifts: patients slipping lately miss more in the last week.
                double share = day <= 7 && story.taken() < 0.8 ? story.taken() - 0.15 : story.taken();
                double roll = random.nextDouble();
                if (roll < share) {
                    log.answer("TAKEN", sent.plus(Duration.ofMinutes(20)));
                } else if (roll < share + (1 - share) / 2) {
                    log.answer("MISSED", sent.plus(Duration.ofMinutes(40)));
                }
                doses.save(log);
            }
        }

        if (labs.findByPatientIdOrderByTakenOnAscRecordedAtAsc(patient.getId()).isEmpty()) {
            double[] values = story.hba1c();
            for (int i = 0; i < values.length; i++) {
                LocalDate taken = today.minusDays(values.length == 1 ? 10 : i == 0 ? 95 : 6);
                labs.save(new LabResult(patient.getId(), "HBA1C", values[i], taken, doctor.getId(), now.minus(Duration.ofDays(1))));
            }
        }

        if (story.teachBackRight() >= 0 && teachBacks.findTop10ByPatientIdOrderByTakenAtDesc(patient.getId()).isEmpty()) {
            String missed = story.teachBackRight() == 3 ? null : story.teachBackRight() == 2 ? "food:0" : "times:0,food:0";
            teachBacks.save(new TeachBack(patient.getId(), patient.getClinic().getId(), plan, 3, story.teachBackRight(), missed,
                    now.minus(Duration.ofDays(12))));
        }

        if (story.noShow() && outcomes.findByPatientIdAndHandledAtIsNull(patient.getId()).isEmpty()) {
            Instant missedAt = today.minusDays(2).atTime(LocalTime.of(10, 30)).atZone(zone).toInstant();
            Appointment booked = appointments.save(new Appointment(patient.getClinic(), patient, missedAt, "Review of blood sugar",
                    doctor.getId(), missedAt.minus(Duration.ofDays(9))));
            outcomes.save(new AppointmentOutcome(booked.getId(), patient.getId(), patient.getClinic().getId(), missedAt,
                    missedAt.plus(Duration.ofHours(2))));
        }
    }

    private static Reading handled(Reading reading, AppUser doctor, Instant at) {
        // Past readings were seen at the time; only today's news should reach the call list.
        reading.markHandled(doctor.getId(), at.plus(Duration.ofHours(2)));
        return reading;
    }
}
