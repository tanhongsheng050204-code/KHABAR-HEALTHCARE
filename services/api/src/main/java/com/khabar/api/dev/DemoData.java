package com.khabar.api.dev;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.followup.ApprovedAnswer;
import com.khabar.api.followup.ApprovedAnswerRepository;
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

/**
 * Fake people for the `local` profile. Every name, IC and phone number here is made up, and the phone
 * numbers use 03-0000 xxxx, which no real line has, so a WhatsApp account configured by mistake can't
 * message a stranger. Four hand-written patients carry the demo story; 26 more come from the generator.
 */
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
    private final ApprovedAnswerRepository answers;

    public DemoData(ClinicRepository clinics, AppUserRepository users, PatientRepository patients,
                    CaregiverLinkRepository caregiverLinks, PatientReplyRepository replies,
                    MedicationItemRepository medications, IntakeSessionRepository intakes, IntakeRecords intakeRecords,
                    ApprovedAnswerRepository answers) {
        this.clinics = clinics;
        this.users = users;
        this.patients = patients;
        this.caregiverLinks = caregiverLinks;
        this.replies = replies;
        this.medications = medications;
        this.intakes = intakes;
        this.intakeRecords = intakeRecords;
        this.answers = answers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsById(DOCTOR_ID)) {
            return;
        }
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya (demo)"));
        AppUser doctor = users.save(new AppUser(DOCTOR_ID, Role.DOCTOR, "Dr Priya", clinic));
        AppUser aminahAccount = users.save(new AppUser(AMINAH_ACCOUNT_ID, Role.PATIENT, "Aminah", null));
        AppUser nurul = users.save(new AppUser(NURUL_ID, Role.CAREGIVER, "Nurul", null));

        LocalDate today = LocalDate.now();
        Patient aminah = inFollowUp(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "03-0000 0001", "ms"), today.minusDays(3));
        Patient rosnah = inFollowUp(new Patient(clinic, null, "Rosnah binti Ahmad", "620505-14-2222", "03-0000 0002", "ms"), today.minusDays(6));
        Patient tan = inFollowUp(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "03-0000 0003", "zh"), today.minusDays(9));
        Patient muthu = inFollowUp(new Patient(clinic, null, "Muthu a/l Rajan", "610815-08-4321", "03-0000 0004", "ta"), today.minusDays(6));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));

        // Replies as if they had come back from the follow-up check-ins (levels as the triage would set them).
        Instant now = Instant.now();
        replies.save(new PatientReply(rosnah, "Sakit dada sejak pagi, rasa sesak sikit", now.minus(Duration.ofMinutes(12)), TriageLevel.RED, "sakit dada"));
        replies.save(new PatientReply(aminah, "Pening dan berpeluh ni", now.minus(Duration.ofMinutes(40)), TriageLevel.WATCH, "pening"));
        replies.save(new PatientReply(tan, "药吃完了，要不要再去拿？", now.minus(Duration.ofHours(3)), TriageLevel.REVIEW, null));
        replies.save(new PatientReply(muthu, "நலம், மருந்து சாப்பிட்டேன்", now.minus(Duration.ofHours(5)), TriageLevel.OK, "நலம்"));

        aminahsIntake(aminah, aminahAccount, now.minus(Duration.ofDays(3)).minus(Duration.ofHours(2)));
        approvedAnswers(clinic, doctor, now.minus(Duration.ofDays(30)));
        generatedPatients(clinic, doctor, now.minus(Duration.ofDays(60)));
    }

    /** 26 more made-up patients so the clinic looks like a clinic: not in follow-up, each with what they take. */
    private void generatedPatients(Clinic clinic, AppUser doctor, Instant when) {
        for (FakePatientGenerator.FakePatient fake : new FakePatientGenerator(2026).generate(26)) {
            Patient patient = new Patient(clinic, null, fake.fullName(), fake.icNumber(), fake.phone(), fake.language());
            patient.recordAllergies(fake.allergies());
            patient.setPregnant(fake.pregnant());
            patients.save(patient);
            fake.medicines().forEach(m -> medications.save(new MedicationItem(patient, m.name(), MedicationItem.Kind.MEDICINE, m.source(),
                    Role.DOCTOR, doctor.getId(), when)));
            fake.herbs().forEach(h -> medications.save(new MedicationItem(patient, h.name(), MedicationItem.Kind.HERB, h.source(),
                    Role.DOCTOR, doctor.getId(), when)));
        }
    }

    /** Answers "Dr Priya" has approved for common follow-up questions. Demo wording; a real clinic writes its own. */
    private void approvedAnswers(Clinic clinic, AppUser doctor, Instant when) {
        answers.save(new ApprovedAnswer(clinic, "Missed a dose",
                List.of("lupa makan ubat", "terlupa makan ubat", "tertinggal ubat", "forgot my medicine", "forgot to take", "missed a dose",
                        "忘记吃药", "忘了吃药", "漏吃", "மறந்துவிட்டேன்"),
                Map.of("ms", "Kalau terlupa satu dos, ambil sebaik sahaja teringat. Kalau sudah hampir waktu dos seterusnya, langkau dos yang tertinggal. Jangan ambil dua dos sekali.",
                        "en", "If you miss a dose, take it as soon as you remember. If it is almost time for the next dose, skip the missed one. Never take two doses at once.",
                        "zh", "如果漏吃一次，想起来就马上吃。如果快到下一次吃药的时间，就跳过漏掉的那一次。不要一次吃两份。",
                        "ta", "ஒரு வேளை மருந்தை மறந்துவிட்டால், நினைவு வந்தவுடன் எடுத்துக்கொள்ளுங்கள். அடுத்த வேளை நேரம் நெருங்கிவிட்டால், மறந்ததை விட்டுவிடுங்கள். இரண்டு வேளை மருந்தை ஒன்றாக எடுக்க வேண்டாம்."),
                doctor, when));
        answers.save(new ApprovedAnswer(clinic, "Medicine running out",
                List.of("ubat habis", "ubat dah habis", "ubat nak habis", "ran out", "running out", "refill", "药吃完", "药快吃完", "拿药", "மருந்து தீர்ந்து"),
                Map.of("ms", "Sila datang ke klinik untuk ambil ubat sebelum ubat habis. Bawa kad temujanji atau paket ubat lama anda.",
                        "en", "Please come to the clinic to collect more before you run out. Bring your appointment card or your old medicine packet.",
                        "zh", "请在药吃完之前回诊所拿药，并带上预约卡或旧的药袋。",
                        "ta", "மருந்து தீர்வதற்கு முன் கிளினிக்கிற்கு வந்து மருந்து பெற்றுக்கொள்ளுங்கள். உங்கள் சந்திப்பு அட்டை அல்லது பழைய மருந்துப் பையைக் கொண்டு வாருங்கள்."),
                doctor, when.plusSeconds(1)));
        answers.save(new ApprovedAnswer(clinic, "Before or after food",
                List.of("sebelum atau selepas makan", "sebelum makan ke", "lepas makan ke", "before or after food", "with food", "饭前还是饭后",
                        "சாப்பாட்டுக்கு முன்பா"),
                Map.of("ms", "Ikut arahan dalam ringkasan lawatan anda: ia menyebut sama ada sebelum atau selepas makan untuk setiap ubat. Kalau tak pasti, tanya ahli farmasi di klinik.",
                        "en", "Follow your visit summary: it says before or after food for each medicine. If you're not sure, ask the pharmacist at the clinic.",
                        "zh", "请按照就诊总结上的说明：每种药都写明饭前或饭后吃。如果不确定，请问诊所的药剂师。",
                        "ta", "உங்கள் சந்திப்புச் சுருக்கத்தைப் பின்பற்றுங்கள்: ஒவ்வொரு மருந்தும் சாப்பாட்டுக்கு முன்பா பின்பா என்று அதில் உள்ளது. சந்தேகம் இருந்தால், கிளினிக் மருந்தாளரிடம் கேளுங்கள்."),
                doctor, when.plusSeconds(2)));
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
