package com.khabar.api.dev;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.intake.IntakeRecords;
import com.khabar.api.intake.IntakeSession;
import com.khabar.api.intake.IntakeSessionRepository;
import com.khabar.api.medications.MedicationItem;
import com.khabar.api.medications.MedicationItemRepository;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.service.AgentDtos.IntakeAnswer;
import com.khabar.api.service.AgentDtos.IntakeFlag;
import com.khabar.api.service.AgentDtos.MedicineMention;
import com.khabar.api.service.AgentDtos.PreVisitReport;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fake people for the `local` profile. Every name, IC and phone number here is made up. */
@Component
@Profile("local")
public class DemoData implements ApplicationRunner {

    public static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final UUID AMINAH_ACCOUNT_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    public static final UUID NURUL_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private final ClinicRepository clinics;
    private final AppUserRepository users;
    private final PatientRepository patients;
    private final CaregiverLinkRepository caregiverLinks;
    private final PatientReplyRepository replies;
    private final MedicationItemRepository medications;
    private final IntakeSessionRepository intakes;
    private final IntakeRecords intakeRecords;

    public DemoData(ClinicRepository clinics, AppUserRepository users, PatientRepository patients,
                    CaregiverLinkRepository caregiverLinks, PatientReplyRepository replies,
                    MedicationItemRepository medications, IntakeSessionRepository intakes, IntakeRecords intakeRecords) {
        this.clinics = clinics;
        this.users = users;
        this.patients = patients;
        this.caregiverLinks = caregiverLinks;
        this.replies = replies;
        this.medications = medications;
        this.intakes = intakes;
        this.intakeRecords = intakeRecords;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsById(DOCTOR_ID)) {
            return;
        }
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya (demo)"));
        users.save(new AppUser(DOCTOR_ID, Role.DOCTOR, "Dr Priya", clinic));
        AppUser aminahAccount = users.save(new AppUser(AMINAH_ACCOUNT_ID, Role.PATIENT, "Aminah", null));
        AppUser nurul = users.save(new AppUser(NURUL_ID, Role.CAREGIVER, "Nurul", null));

        LocalDate today = LocalDate.now();
        Patient aminah = inFollowUp(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"), today.minusDays(3));
        Patient rosnah = inFollowUp(new Patient(clinic, null, "Rosnah binti Ahmad", "620505-14-2222", "013-111 2222", "ms"), today.minusDays(6));
        Patient tan = inFollowUp(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"), today.minusDays(9));
        Patient muthu = inFollowUp(new Patient(clinic, null, "Muthu a/l Rajan", "610815-08-4321", "019-444 5555", "ta"), today.minusDays(6));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));

        // Replies as if they had come back from the follow-up check-ins (levels as the triage would set them).
        Instant now = Instant.now();
        replies.save(new PatientReply(rosnah, "Sakit dada sejak pagi, rasa sesak sikit", now.minus(Duration.ofMinutes(12)), TriageLevel.RED, "sakit dada"));
        replies.save(new PatientReply(aminah, "Pening dan berpeluh ni", now.minus(Duration.ofMinutes(40)), TriageLevel.WATCH, "pening"));
        replies.save(new PatientReply(tan, "药吃完了，要不要再去拿？", now.minus(Duration.ofHours(3)), TriageLevel.REVIEW, null));
        replies.save(new PatientReply(muthu, "நலம், மருந்து சாப்பிட்டேன்", now.minus(Duration.ofHours(5)), TriageLevel.OK, "நலம்"));

        aminahsIntake(aminah, aminahAccount, now.minus(Duration.ofDays(3)).minus(Duration.ofHours(2)));
    }

    /**
     * Aminah's pre-visit chat, and what she takes from other places: metformin from two clinics under
     * two names, and bitter gourd juice. Prescribing metformin to her shows the duplicate and herb checks.
     */
    private void aminahsIntake(Patient aminah, AppUser aminahAccount, Instant when) {
        List<String[]> chat = List.of(
                new String[]{"reason", "Apa sebab Mak Cik / Pak Cik datang ke klinik hari ini?", "Pening sejak 3 hari, kadang-kadang berpeluh."},
                new String[]{"conditions", "Ada penyakit jangka panjang, contohnya kencing manis atau darah tinggi?", "Kencing manis dan darah tinggi."},
                new String[]{"medicines", "Apa ubat yang sedang diambil? Termasuk ubat dari klinik lain, supplemen, jamu atau ubat tradisional.",
                        "Metformin dari klinik kesihatan, Brand A 500mg dari GP, dan jus peria yang kakak buat."},
                new String[]{"allergies", "Ada alahan pada mana-mana ubat?", "Tak ada."});
        List<Map<String, String>> transcript = new ArrayList<>();
        List<IntakeAnswer> answers = new ArrayList<>();
        for (String[] turn : chat) {
            transcript.add(Map.of("role", "assistant", "content", turn[1]));
            transcript.add(Map.of("role", "user", "content", turn[2]));
            answers.add(new IntakeAnswer(turn[0], turn[1], turn[2]));
        }
        transcript.add(Map.of("role", "assistant", "content", "Terima kasih. Doktor akan baca maklumat ini sebelum berjumpa."));
        PreVisitReport report = new PreVisitReport(chat.get(0)[2], answers,
                List.of(new MedicineMention("Metformin", "metformin"), new MedicineMention("Brand A 500mg", "metformin")),
                List.of("peria"), List.of(), List.of(), List.of(new IntakeFlag("watch", "pening")));

        IntakeSession session = new IntakeSession(aminah, when.minus(Duration.ofMinutes(6)));
        session.update(intakeRecords.toJson(transcript), when);
        session.complete(intakeRecords.toJson(report), when);
        intakes.save(session);

        medications.save(new MedicationItem(aminah, "Metformin 500mg", MedicationItem.Kind.MEDICINE, "Klinik Kesihatan", Role.PATIENT, aminahAccount.getId(), when));
        medications.save(new MedicationItem(aminah, "Brand A 500mg", MedicationItem.Kind.MEDICINE, "GP clinic", Role.PATIENT, aminahAccount.getId(), when.plusSeconds(1)));
        medications.save(new MedicationItem(aminah, "Jus peria (bitter gourd)", MedicationItem.Kind.HERB, "Made by her sister", Role.PATIENT, aminahAccount.getId(), when.plusSeconds(2)));
    }

    private Patient inFollowUp(Patient patient, LocalDate visitDay) {
        patient.startFollowUp(visitDay);
        return patients.save(patient);
    }
}
