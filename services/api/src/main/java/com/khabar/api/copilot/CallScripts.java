package com.khabar.api.copilot;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.insights.RiskScores;
import com.khabar.api.messaging.PatientMessages;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.scheduling.Appointment;
import com.khabar.api.scheduling.AppointmentRepository;
import com.khabar.api.service.AgentClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Before a nurse calls, a short script in English and the patient's language: an opening, open questions about what
 * the record shows, and a check on the next visit. The AI drafts it from redacted replies and coded facts; without a
 * model, Khabar's own phrases do. Every script ends with the clinic's fixed emergency sentence, never AI wording.
 * It suggests what to ask; it never says what to prescribe.
 */
@RestController
public class CallScripts {

    private static final Logger log = LoggerFactory.getLogger(CallScripts.class);

    public record Line(String english, String spoken) {
    }

    /** source is "model" or "rules". */
    public record Script(String language, List<Line> lines, Line safety, String source) {
    }

    private static final Map<String, Map<String, String>> PHRASES = phrases();

    private static Map<String, Map<String, String>> phrases() {
        Map<String, Map<String, String>> p = new LinkedHashMap<>();
        p.put("OPEN", Map.of("en", "Hello, this is your clinic calling to see how you are.",
                "ms", "Assalamualaikum / selamat sejahtera, ini dari klinik anda. Kami telefon untuk bertanya khabar.",
                "zh", "您好，我们是您的诊所，打电话来关心一下您的近况。",
                "ta", "வணக்கம், உங்கள் கிளினிக்கிலிருந்து அழைக்கிறோம். நீங்கள் எப்படி இருக்கிறீர்கள் என்று கேட்க.") );
        p.put("MISSED_DOSES", Map.of("en", "Your reminders show some missed doses this week. What has made it hard to take them?",
                "ms", "Peringatan anda menunjukkan beberapa dos terlepas minggu ini. Apa yang menyukarkan anda makan ubat?",
                "zh", "提醒记录显示这周漏吃了几次药。是什么让您不方便按时吃药？",
                "ta", "இந்த வாரம் சில வேளை மருந்து தவறியதாக நினைவூட்டல்கள் காட்டுகின்றன. மருந்து எடுப்பதில் என்ன சிரமம்?"));
        p.put("UNANSWERED_DOSES", Map.of("en", "Have the medicine reminders been reaching you? Are they at a good time?",
                "ms", "Adakah peringatan ubat sampai kepada anda? Adakah masanya sesuai?",
                "zh", "吃药提醒您收到了吗？时间合适吗？",
                "ta", "மருந்து நினைவூட்டல்கள் உங்களுக்கு வருகின்றனவா? நேரம் சரியாக உள்ளதா?"));
        p.put("SUGAR", Map.of("en", "Your blood sugar readings have been higher lately. How have you been feeling?",
                "ms", "Bacaan gula darah anda lebih tinggi kebelakangan ini. Bagaimana perasaan anda?",
                "zh", "您最近的血糖读数偏高。您感觉怎么样？",
                "ta", "சமீபத்தில் உங்கள் இரத்தச் சர்க்கரை அளவு அதிகமாக உள்ளது. நீங்கள் எப்படி உணர்கிறீர்கள்?"));
        p.put("PRESSURE", Map.of("en", "Your blood pressure readings have been higher lately. Any headaches or dizziness?",
                "ms", "Bacaan tekanan darah anda lebih tinggi kebelakangan ini. Ada sakit kepala atau pening?",
                "zh", "您最近的血压读数偏高。有没有头痛或头晕？",
                "ta", "சமீபத்தில் உங்கள் இரத்த அழுத்தம் அதிகமாக உள்ளது. தலைவலி அல்லது தலைச்சுற்றல் உள்ளதா?"));
        p.put("NO_REPLY", Map.of("en", "We have not heard from you for a few days. Is everything all right?",
                "ms", "Kami tidak mendengar khabar anda beberapa hari. Semuanya baik?",
                "zh", "我们几天没有收到您的消息了。一切都好吗？",
                "ta", "சில நாட்களாக உங்களிடமிருந்து எதுவும் வரவில்லை. எல்லாம் சரியா?"));
        p.put("MISSED_VISIT", Map.of("en", "We missed you at your appointment. Would you like to choose a new time?",
                "ms", "Anda tidak hadir temu janji. Mahu pilih masa baharu?",
                "zh", "您错过了预约。要不要重新约个时间？",
                "ta", "உங்கள் சந்திப்புக்கு நீங்கள் வரவில்லை. புதிய நேரத்தைத் தேர்ந்தெடுக்க விரும்புகிறீர்களா?"));
        p.put("PLAN", Map.of("en", "Can you tell me how you take your medicines each day?",
                "ms", "Boleh ceritakan bagaimana anda makan ubat setiap hari?",
                "zh", "可以告诉我您每天是怎么吃药的吗？",
                "ta", "ஒவ்வொரு நாளும் மருந்துகளை எப்படி எடுக்கிறீர்கள் என்று சொல்ல முடியுமா?"));
        p.put("REPLY", Map.of("en", "You sent us a message recently. Can you tell me more about how you are now?",
                "ms", "Anda ada menghantar mesej baru-baru ini. Boleh ceritakan keadaan anda sekarang?",
                "zh", "您最近给我们发了信息。可以再说说您现在的情况吗？",
                "ta", "சமீபத்தில் எங்களுக்குச் செய்தி அனுப்பினீர்கள். இப்போது எப்படி இருக்கிறீர்கள் என்று மேலும் சொல்ல முடியுமா?"));
        p.put("CLOSE", Map.of("en", "Do you know when your next visit is?",
                "ms", "Anda tahu bila lawatan anda yang seterusnya?",
                "zh", "您知道下次复诊是什么时候吗？",
                "ta", "உங்கள் அடுத்த வருகை எப்போது என்று தெரியுமா?"));
        return p;
    }

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final PatientRepository patients;
    private final PatientReplyRepository replies;
    private final RiskScores risk;
    private final Pathways pathways;
    private final AppointmentRepository appointments;
    private final AgentClientService agents;
    private final AiReceipts aiReceipts;
    private final AuditLog auditLog;
    private final ObjectMapper json;
    private final AdjustableClock clock;

    public CallScripts(CurrentUser currentUser, ClinicStaffAccess staffAccess, PatientRepository patients, PatientReplyRepository replies,
                       RiskScores risk, Pathways pathways, AppointmentRepository appointments, AgentClientService agents,
                       AiReceipts aiReceipts, AuditLog auditLog, ObjectMapper json, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.patients = patients;
        this.replies = replies;
        this.risk = risk;
        this.pathways = pathways;
        this.appointments = appointments;
        this.agents = agents;
        this.aiReceipts = aiReceipts;
        this.auditLog = auditLog;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping("/api/patients/{id}/call-script")
    @Transactional
    public Script script(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser staff = currentUser.from(jwt);
        if (staff.getClinic() == null || !staffAccess.canManageFollowUp(staff)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Call scripts are for the clinic's follow-up staff.");
        }
        Patient patient = patients.findById(id).filter(p -> p.getClinic().getId().equals(staff.getClinic().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        auditLog.record(staff, patient.getId(), AuditAction.VIEWED_RECORD);

        String language = patient.getPreferredLanguage() == null ? "ms" : patient.getPreferredLanguage();
        RiskScores.Score score = risk.score(patient.getId(), staff.getClinic().getId());
        List<String> concerns = concerns(score);
        List<PatientReply> recent = replies.findTop5ByPatientIdOrderByReceivedAtDesc(patient.getId()).stream()
                .filter(r -> r.getHandledAt() == null).limit(3).toList();
        if (!recent.isEmpty()) {
            concerns.add(0, "REPLY");
        }
        Line safety = new Line(PatientMessages.emergencyAdviceText("en"), PatientMessages.emergencyAdviceText(language));

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("pathways", pathways.active(patient.getId()).stream().map(p -> Pathways.DEFINITIONS.get(p.getPathway()).label()).toList());
        context.put("concerns", score.withoutValues().reasons().stream().map(RiskScores.Reason::label).toList());
        context.put("recentMessagesRedacted", recent.stream().map(r -> Redactor.redact(r.getText(), patient)).toList());
        context.put("nextVisitBooked", appointments.findFirstByPatientIdAndStatusAndStartsAtAfterOrderByStartsAt(
                patient.getId(), Appointment.Status.BOOKED, clock.instant()).isPresent());
        try {
            aiReceipts.text(patient, AiReceipts.Purpose.CALL_SCRIPT, "/agents/copilot/call-script", json.writeValueAsString(context));
            Map<String, Object> result = agents.callScript(language, context);
            List<Line> lines = new ArrayList<>();
            if (result != null && result.get("lines") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m && m.get("english") != null && m.get("spoken") != null) {
                        lines.add(new Line(String.valueOf(m.get("english")), String.valueOf(m.get("spoken"))));
                    }
                }
            }
            if (!lines.isEmpty()) {
                return new Script(language, lines, safety, "model");
            }
        } catch (Exception e) {
            log.info("Call script model unavailable, using Khabar's phrases: {}", e.getClass().getSimpleName());
        }
        List<Line> lines = new ArrayList<>();
        lines.add(line("OPEN", language));
        concerns.stream().distinct().forEach(c -> lines.add(line(c, language)));
        lines.add(line("CLOSE", language));
        return new Script(language, lines, safety, "rules");
    }

    private static List<String> concerns(RiskScores.Score score) {
        List<String> out = new ArrayList<>();
        for (RiskScores.Reason r : score.reasons()) {
            switch (r.key()) {
                case "MISSED_DOSES" -> out.add("MISSED_DOSES");
                case "UNANSWERED_DOSES" -> out.add("UNANSWERED_DOSES");
                case "GLUCOSE_WORSENING", "GLUCOSE_ABOVE_TARGET" -> out.add("SUGAR");
                case "BP_WORSENING", "BP_ABOVE_TARGET" -> out.add("PRESSURE");
                case "NO_REPLY" -> out.add("NO_REPLY");
                case "MISSED_VISIT" -> out.add("MISSED_VISIT");
                case "PLAN_NOT_UNDERSTOOD" -> out.add("PLAN");
                default -> { }
            }
        }
        return out;
    }

    private static Line line(String key, String language) {
        Map<String, String> phrase = PHRASES.get(key);
        return new Line(phrase.get("en"), phrase.getOrDefault(language, phrase.get("en")));
    }
}
