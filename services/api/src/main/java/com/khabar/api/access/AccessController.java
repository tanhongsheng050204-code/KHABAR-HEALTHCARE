package com.khabar.api.access;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.FollowUpService;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.privacy.AiReceipts;
import com.khabar.api.reminders.Reminders;
import com.khabar.api.service.AgentClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ways in for people who find the standard app hard: voice replies, a simple large-print screen, a family Telegram
 * group, and a caregiver who answers for the patient. Whatever a caregiver does is recorded as theirs.
 */
@RestController
public class AccessController {

    private static final Logger log = LoggerFactory.getLogger(AccessController.class);
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    static final int MAX_REPLY = 2000;

    public record Preferences(boolean voiceFirst, boolean simpleMode) {
    }

    public record Transcript(String transcript) {
    }

    public record Text(String text) {
    }

    public record DoseAnswer(String response) {
    }

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientPreferencesRepository preferences;
    private final FamilyGroups family;
    private final AgentClientService agents;
    private final AiReceipts aiReceipts;
    private final CaregiverLinkRepository caregiverLinks;
    private final FollowUpService followUp;
    private final Reminders reminders;
    private final AdjustableClock clock;

    public AccessController(CurrentUser currentUser, PatientRepository patients, PatientPreferencesRepository preferences, FamilyGroups family,
                            AgentClientService agents, AiReceipts aiReceipts, CaregiverLinkRepository caregiverLinks, FollowUpService followUp,
                            Reminders reminders, AdjustableClock clock) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.preferences = preferences;
        this.family = family;
        this.agents = agents;
        this.aiReceipts = aiReceipts;
        this.caregiverLinks = caregiverLinks;
        this.followUp = followUp;
        this.reminders = reminders;
        this.clock = clock;
    }

    // ---- The patient's own -------------------------------------------------------------------------------------

    @GetMapping("/api/me/preferences")
    @Transactional(readOnly = true)
    public Preferences preferences(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(jwt);
        PatientPreferences p = preferences.findById(patient.getId()).orElseGet(() -> new PatientPreferences(patient.getId()));
        return new Preferences(p.isVoiceFirst(), p.isSimpleMode());
    }

    @PutMapping("/api/me/preferences")
    @Transactional
    public Preferences update(@RequestBody Preferences input, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(jwt);
        PatientPreferences p = preferences.findById(patient.getId()).orElseGet(() -> new PatientPreferences(patient.getId()));
        p.update(input.voiceFirst(), input.simpleMode(), clock.instant());
        preferences.save(p);
        return new Preferences(p.isVoiceFirst(), p.isSimpleMode());
    }

    /** A spoken reply recorded in the app, turned into text for the patient to check before sending. Not kept. */
    @PostMapping(path = "/api/me/voice-reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Transcript voiceReply(@RequestParam("audio") MultipartFile audio, @AuthenticationPrincipal Jwt jwt) throws IOException {
        Patient patient = ownRecord(jwt);
        if (audio.isEmpty() || audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record a shorter message (up to 10 MB).");
        }
        aiReceipts.media(patient, AiReceipts.Purpose.VOICE_REPLY, "/agents/transcribe",
                "A voice reply recorded in the app (" + Math.max(1, audio.getSize() / 1024) + " KB). It is turned into text and not kept.");
        try {
            String name = audio.getOriginalFilename() == null || audio.getOriginalFilename().isBlank() ? "voice.webm" : audio.getOriginalFilename();
            String text = agents.transcribe(audio.getBytes(), name, patient.getPreferredLanguage());
            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nothing could be heard. Try again or type your reply.");
            }
            return new Transcript(text.strip());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Voice reply not transcribed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Voice replies are unavailable right now. Please type your reply.");
        }
    }

    @GetMapping("/api/me/family-group")
    @Transactional(readOnly = true)
    public FamilyGroups.Status familyGroup(@AuthenticationPrincipal Jwt jwt) {
        return family.status(ownRecord(jwt).getId());
    }

    @PostMapping("/api/me/family-group/link-code")
    @Transactional
    public FamilyGroups.LinkCode familyCode(@AuthenticationPrincipal Jwt jwt) {
        return family.newLinkCode(ownRecord(jwt).getId());
    }

    @DeleteMapping("/api/me/family-group")
    @Transactional
    public FamilyGroups.Status unlinkFamily(@AuthenticationPrincipal Jwt jwt) {
        UUID id = ownRecord(jwt).getId();
        family.unlink(id);
        return family.status(id);
    }

    // ---- A caregiver acting for the patient ----------------------------------------------------------------------

    /** Dose reminders from the last two days still waiting for an answer. */
    @GetMapping("/api/patients/{id}/doses-waiting")
    @Transactional(readOnly = true)
    public List<Reminders.DoseView> dosesWaiting(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = caregiversPatient(jwt, id).patient();
        LocalDate from = LocalDate.now(clock).minusDays(2);
        return reminders.adherence(patient.getId(), 3).doses().stream().filter(d -> d.response() == null && !d.day().isBefore(from)).toList();
    }

    @PostMapping("/api/patients/{id}/doses/{doseId}")
    @Transactional
    public Reminders.Answer answerDose(@PathVariable UUID id, @PathVariable UUID doseId, @RequestBody DoseAnswer input,
                                       @AuthenticationPrincipal Jwt jwt) {
        Patient patient = caregiversPatient(jwt, id).patient();
        String response = input.response() == null ? "" : input.response().trim().toUpperCase(Locale.ROOT);
        if (!response.equals("TAKEN") && !response.equals("MISSED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer TAKEN or MISSED.");
        }
        Reminders.Answer answer = reminders.answerDose(patient, doseId, response, "CAREGIVER");
        if (!answer.recorded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That reminder was already answered or is too old.");
        }
        return answer;
    }

    /** A check-in reply written by a caregiver for the patient. The clinic sees who wrote it; triage reads it as usual. */
    @PostMapping("/api/patients/{id}/replies-on-behalf")
    @Transactional
    public FollowUpService.Outcome replyOnBehalf(@PathVariable UUID id, @RequestBody Text input, @AuthenticationPrincipal Jwt jwt) {
        Acting acting = caregiversPatient(jwt, id);
        String text = input.text() == null ? "" : input.text().strip();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write how the patient is.");
        }
        if (text.length() > MAX_REPLY) {
            text = text.substring(0, MAX_REPLY);
        }
        return followUp.receiveReply(acting.patient(), "[" + acting.caregiver().getDisplayName() + ", caregiver] " + text);
    }

    record Acting(AppUser caregiver, Patient patient) {
    }

    private Acting caregiversPatient(Jwt jwt, UUID patientId) {
        AppUser user = currentUser.from(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.CAREGIVER
                || !caregiverLinks.existsByPatientIdAndCaregiverIdAndRevokedAtIsNullAndScope(patientId, user.getId(), CaregiverScope.SUMMARY_AND_ALERTS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a caregiver the patient shares alerts with can act for them.");
        }
        return new Acting(user, patient);
    }

    private Patient ownRecord(Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is the patient's own setting.");
        }
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }
}
