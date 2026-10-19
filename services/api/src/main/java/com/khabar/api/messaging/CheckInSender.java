package com.khabar.api.messaging;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CheckIn;
import com.khabar.api.followup.CheckInRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Sends every check-in that is due today or earlier and has not been sent yet. */
@Service
public class CheckInSender {

    private static final Logger log = LoggerFactory.getLogger(CheckInSender.class);

    private final CheckInRepository checkIns;
    private final PatientMessages messages;
    private final AdjustableClock clock;
    private final com.khabar.api.pathways.Pathways pathways;
    @org.springframework.beans.factory.annotation.Autowired
    private com.khabar.api.access.PatientPreferencesRepository preferences;

    private static final java.util.Map<String, String> VOICE_HINT = java.util.Map.of(
            "ms", "🎤 Lebih mudah bercakap? Tekan dan tahan butang mikrofon untuk membalas dengan suara.",
            "en", "🎤 Easier to talk? Press and hold the microphone button to reply by voice.",
            "zh", "🎤 说话更方便？按住麦克风按钮用语音回复。",
            "ta", "🎤 பேசுவது எளிதா? குரலில் பதிலளிக்க மைக்ரோஃபோன் பொத்தானை அழுத்திப் பிடிக்கவும்.");

    public CheckInSender(CheckInRepository checkIns, PatientMessages messages, AdjustableClock clock,
                         com.khabar.api.pathways.Pathways pathways) {
        this.pathways = pathways;
        this.checkIns = checkIns;
        this.messages = messages;
        this.clock = clock;
    }

    /** A check-in unanswered for this long gets one gentle re-ask. */
    static final java.time.Duration REMIND_AFTER = java.time.Duration.ofHours(24);

    /**
     * The no-reply sequence, step one: one gentle re-ask a day after an unanswered check-in. Step two needs no
     * message: after 48 hours the patient is on the clinic's call list, and a caregiver with alert consent sees
     * that the patient has gone quiet. Returns how many re-asks were delivered.
     */
    @Transactional
    public int sendReminders() {
        int delivered = 0;
        for (CheckIn checkIn : checkIns.findByStatusAndSentAtBeforeAndReminderSentAtIsNull(CheckIn.Status.SENT, clock.instant().minus(REMIND_AFTER))) {
            Messenger.Result result = messages.send(checkIn.getPatient(), PatientMessages.reminderText(checkIn.getPatient().getPreferredLanguage()),
                    Messenger.Kind.CHECK_IN);
            // Marked either way: a failed re-ask is not retried, so a patient never gets several.
            checkIn.markReminded(clock.instant());
            if (result.delivered()) {
                delivered++;
            } else {
                log.warn("Re-ask for check-in {} not delivered: {}", checkIn.getId(), result.error());
            }
        }
        return delivered;
    }

    /** Returns how many check-ins were delivered. */
    @Transactional
    public int sendDue() {
        List<CheckIn> due = checkIns.findByStatusAndDueDateLessThanEqual(CheckIn.Status.PENDING, LocalDate.now(clock));
        int delivered = 0;
        for (CheckIn checkIn : due) {
            String language = checkIn.getPatient().getPreferredLanguage();
            // A patient on a condition pathway is also asked for what that pathway tracks.
            String text = PatientMessages.checkInText(language, checkIn.isFasting()) + pathways.checkInExtra(checkIn.getPatient().getId(), language);
            if (preferences.findById(checkIn.getPatient().getId()).map(com.khabar.api.access.PatientPreferences::isVoiceFirst).orElse(false)) {
                text = text + "\n" + VOICE_HINT.getOrDefault(language, VOICE_HINT.get("en"));
            }
            Messenger.Result result = messages.send(checkIn.getPatient(), text, Messenger.Kind.CHECK_IN);
            if (result.delivered()) {
                checkIn.markSent(clock.instant());
                delivered++;
            } else {
                checkIn.markFailed();
                log.warn("Check-in {} (day {}) not delivered: {}", checkIn.getId(), checkIn.getDayNumber(), result.error());
            }
        }
        return delivered;
    }
}
