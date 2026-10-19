package com.khabar.api.reminders;

import com.khabar.api.caregivers.CaregiverAlerts;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.encounters.Encounter;
import com.khabar.api.encounters.EncounterRepository;
import com.khabar.api.encounters.PrescriptionLine;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.OutboundMessage;
import com.khabar.api.messaging.OutboundMessageRepository;
import com.khabar.api.messaging.PatientMessages;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.TelegramChatIndex;
import com.khabar.api.scheduling.Appointment;
import com.khabar.api.scheduling.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dose, refill and appointment reminders on Telegram, which a patient switches on themselves, and no-show
 * detection for the clinic. A dose reminder lists the medicines the doctor prescribed for that time of day, with
 * Taken and Missed buttons; two missed doses in three days put the patient on the call list and tell caregivers who
 * share alerts. Reminders only repeat the doctor's own plan; they never advise.
 */
@Service
public class Reminders {

    private static final Logger log = LoggerFactory.getLogger(Reminders.class);

    /** Local clock time of each dose. While fasting, morning doses move to sahur and the rest to berbuka. */
    static final Map<String, LocalTime> SLOT_TIMES = orderedSlots();
    /** A dose reminder goes out within this long of its time; a late server never sends yesterday's doses. */
    static final Duration SEND_WINDOW = Duration.ofHours(3);
    /** Taken or Missed can be answered for this many days. */
    static final int ANSWER_DAYS = 2;
    static final int MISSED_TO_FLAG = 2;
    static final int MISSED_WINDOW_DAYS = 3;
    static final Duration DEFAULT_PLAN_LENGTH = Duration.ofDays(30);
    static final LocalTime APPOINTMENT_REMINDER_FROM = LocalTime.of(18, 0);
    static final LocalTime APPOINTMENT_REMINDER_UNTIL = LocalTime.of(22, 0);
    static final LocalTime REFILL_FROM = LocalTime.of(9, 0);
    static final LocalTime REFILL_UNTIL = LocalTime.of(20, 0);
    static final int REFILL_DAYS_BEFORE = 3;
    /** An appointment counts as missed when no visit was started for the patient that day and this long has passed. */
    static final Duration NO_SHOW_AFTER = Duration.ofHours(2);
    static final String REFILL = "REFILL";
    static final String APPOINTMENT = "APPOINTMENT";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public record DoseView(UUID id, LocalDate day, String slot, String medicines, String response, Instant sentAt) {
    }

    public record Adherence(int days, int sent, int taken, int missed, int unanswered, List<DoseView> doses) {
    }

    public record SettingsView(boolean doseReminders, boolean appointmentReminders, boolean telegramLinked, boolean hasPlan,
                               Adherence adherence) {
    }

    /** What happened to a Taken / Missed / Coming / Can't-come answer. */
    public record Answer(boolean recorded, String message) {
    }

    private final ReminderSettingsRepository settings;
    private final DoseLogRepository doses;
    private final ReminderLogRepository reminderLogs;
    private final AppointmentOutcomeRepository outcomes;
    private final PatientRepository patients;
    private final EncounterRepository encounters;
    private final AppointmentRepository appointments;
    private final PatientReplyRepository replies;
    private final PatientMessages messages;
    private final OutboundMessageRepository outbox;
    private final TelegramBotClient bot;
    private final TelegramChatIndex chatIndex;
    private final CaregiverAlerts caregivers;
    private final AdjustableClock clock;
    private final TransactionTemplate tx;

    public Reminders(ReminderSettingsRepository settings, DoseLogRepository doses, ReminderLogRepository reminderLogs,
                     AppointmentOutcomeRepository outcomes, PatientRepository patients, EncounterRepository encounters,
                     AppointmentRepository appointments, PatientReplyRepository replies, PatientMessages messages,
                     OutboundMessageRepository outbox, TelegramBotClient bot, TelegramChatIndex chatIndex,
                     CaregiverAlerts caregivers, AdjustableClock clock, TransactionTemplate tx) {
        this.settings = settings;
        this.doses = doses;
        this.reminderLogs = reminderLogs;
        this.outcomes = outcomes;
        this.patients = patients;
        this.encounters = encounters;
        this.appointments = appointments;
        this.replies = replies;
        this.messages = messages;
        this.outbox = outbox;
        this.bot = bot;
        this.chatIndex = chatIndex;
        this.caregivers = caregivers;
        this.clock = clock;
        this.tx = tx;
    }

    private static Map<String, LocalTime> orderedSlots() {
        Map<String, LocalTime> slots = new LinkedHashMap<>();
        slots.put("sahur", LocalTime.of(5, 0));
        slots.put("morning", LocalTime.of(8, 0));
        slots.put("afternoon", LocalTime.of(13, 0));
        slots.put("evening", LocalTime.of(18, 0));
        slots.put("berbuka", LocalTime.of(19, 15));
        slots.put("night", LocalTime.of(21, 0));
        return slots;
    }

    // ---- Settings and adherence -----------------------------------------------------------------------------

    public SettingsView settingsFor(Patient patient) {
        ReminderSettings s = settings.findById(patient.getId()).orElseGet(() -> new ReminderSettings(patient.getId()));
        return new SettingsView(s.isDoseReminders(), s.isAppointmentReminders(), patient.getTelegramChatId() != null,
                currentPlan(patient.getId(), clock.instant()).isPresent(), adherence(patient.getId(), 14));
    }

    public SettingsView update(Patient patient, boolean doseReminders, boolean appointmentReminders) {
        ReminderSettings s = settings.findById(patient.getId()).orElseGet(() -> new ReminderSettings(patient.getId()));
        s.update(doseReminders, appointmentReminders, clock.instant());
        settings.save(s);
        return settingsFor(patient);
    }

    public Adherence adherence(UUID patientId, int days) {
        LocalDate from = today().minusDays(days - 1L);
        List<DoseView> rows = doses.findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(patientId, from).stream()
                .map(d -> new DoseView(d.getId(), d.getDay(), d.getSlot(), d.getMedicines(), d.getResponse(), d.getSentAt()))
                .toList();
        int taken = (int) rows.stream().filter(d -> "TAKEN".equals(d.response())).count();
        int missed = (int) rows.stream().filter(d -> "MISSED".equals(d.response())).count();
        return new Adherence(days, rows.size(), taken, missed, rows.size() - taken - missed, rows);
    }

    // ---- The scheduler's work -------------------------------------------------------------------------------

    public void runAll() {
        step("doses", this::runDoses);
        step("refills", this::runRefills);
        step("appointments", this::runAppointmentReminders);
        step("no-shows", this::detectNoShows);
        step("caregivers", caregivers::checkQuiet);
    }

    private static void step(String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Reminder step {} failed: {}", name, e.getClass().getSimpleName());
        }
    }

    /** Sends each dose reminder whose time has come, once per patient, day and time of day. */
    public int runDoses() {
        int sent = 0;
        for (ReminderSettings s : settings.findByDoseRemindersTrue()) {
            Integer n = tx.execute(status -> patients.findById(s.getPatientId()).map(this::dosesFor).orElse(0));
            sent += n == null ? 0 : n;
        }
        return sent;
    }

    private int dosesFor(Patient patient) {
        Instant now = clock.instant();
        Optional<Encounter> plan = currentPlan(patient.getId(), now);
        if (plan.isEmpty()) {
            return 0;
        }
        LocalDate today = today();
        String language = patient.getPreferredLanguage();
        int sent = 0;
        for (Map.Entry<String, List<PrescriptionLine>> slot : bySlot(plan.get()).entrySet()) {
            Instant due = ZonedDateTime.of(today, SLOT_TIMES.get(slot.getKey()), zone()).toInstant();
            if (now.isBefore(due) || !now.isBefore(due.plus(SEND_WINDOW)) || due.isBefore(plan.get().getFinalisedAt())
                    || doses.existsByPatientIdAndDayAndSlot(patient.getId(), today, slot.getKey())) {
                continue;
            }
            List<String> lines = slot.getValue().stream().map(rx -> ReminderTexts.medicineLine(language, rx)).toList();
            DoseLog entry = doses.saveAndFlush(new DoseLog(patient.getId(), plan.get().getId(), today, slot.getKey(),
                    String.join("\n", lines), now));
            String text = ReminderTexts.dose(language, slot.getKey(), lines);
            entry.delivered(send(patient, text, buttons(
                    button(ReminderTexts.TAKEN_BUTTON, "d:" + entry.getId() + ":T"),
                    button(ReminderTexts.MISSED_BUTTON, "d:" + entry.getId() + ":M"))));
            sent++;
        }
        return sent;
    }

    /** Medicines grouped by when they are taken, in clock order. As-needed medicines get no reminder. */
    static Map<String, List<PrescriptionLine>> bySlot(Encounter plan) {
        Map<String, List<PrescriptionLine>> grouped = new LinkedHashMap<>();
        SLOT_TIMES.keySet().forEach(slot -> grouped.put(slot, new ArrayList<>()));
        for (PrescriptionLine rx : plan.getPrescription()) {
            if (rx.isAsNeeded()) {
                continue;
            }
            for (String slot : rx.slots()) {
                String at = plan.isFasting() ? fastingSlot(slot.trim()) : slot.trim();
                if (grouped.containsKey(at) && !grouped.get(at).contains(rx)) {
                    grouped.get(at).add(rx);
                }
            }
        }
        grouped.values().removeIf(List::isEmpty);
        return grouped;
    }

    private static String fastingSlot(String slot) {
        return switch (slot) {
            case "morning" -> "sahur";
            case "afternoon", "evening", "night" -> "berbuka";
            default -> slot;
        };
    }

    /** The latest signed visit, while its medicines are still meant to be taken. */
    Optional<Encounter> currentPlan(UUID patientId, Instant now) {
        return encounters.findFirstByPatientIdAndStatusOrderByFinalisedAtDesc(patientId, Encounter.Status.FINAL)
                .filter(e -> e.getFinalisedAt() != null && now.isBefore(e.getFinalisedAt().plus(planLength(e))));
    }

    private static Duration planLength(Encounter e) {
        return e.getFollowUpWeeks() == null || e.getFollowUpWeeks() <= 0
                ? DEFAULT_PLAN_LENGTH : Duration.ofHours(Math.round(e.getFollowUpWeeks() * 7 * 24));
    }

    /** Three days before the medicines run out, unless the patient already has a visit booked. */
    public int runRefills() {
        LocalTime local = clock.instant().atZone(zone()).toLocalTime();
        if (local.isBefore(REFILL_FROM) || !local.isBefore(REFILL_UNTIL)) {
            return 0;
        }
        int sent = 0;
        for (ReminderSettings s : settings.findByDoseRemindersTrue()) {
            Boolean done = tx.execute(status -> patients.findById(s.getPatientId()).map(this::refillFor).orElse(false));
            sent += Boolean.TRUE.equals(done) ? 1 : 0;
        }
        return sent;
    }

    private boolean refillFor(Patient patient) {
        Instant now = clock.instant();
        Optional<Encounter> plan = currentPlan(patient.getId(), now).filter(e -> e.getFollowUpWeeks() != null && e.getFollowUpWeeks() > 0);
        if (plan.isEmpty() || bySlot(plan.get()).isEmpty()) {
            return false;
        }
        Instant runOut = plan.get().getFinalisedAt().plus(planLength(plan.get()));
        if (now.isBefore(runOut.minus(Duration.ofDays(REFILL_DAYS_BEFORE))) || reminderLogs.existsByKindAndRef(REFILL, plan.get().getId())
                || appointments.findFirstByPatientIdAndStatusAndStartsAtAfterOrderByStartsAt(patient.getId(), Appointment.Status.BOOKED, now).isPresent()) {
            return false;
        }
        ReminderLog entry = reminderLogs.saveAndFlush(new ReminderLog(patient.getId(), REFILL, plan.get().getId(), now));
        entry.delivered(send(patient, ReminderTexts.refill(patient.getPreferredLanguage(), DATE.format(runOut.atZone(zone()))), null));
        return true;
    }

    /** The evening before a booked visit, for patients who asked for appointment reminders. */
    public int runAppointmentReminders() {
        ZonedDateTime local = clock.instant().atZone(zone());
        if (local.toLocalTime().isBefore(APPOINTMENT_REMINDER_FROM) || !local.toLocalTime().isBefore(APPOINTMENT_REMINDER_UNTIL)) {
            return 0;
        }
        LocalDate tomorrow = local.toLocalDate().plusDays(1);
        Instant from = tomorrow.atStartOfDay(zone()).toInstant();
        Instant to = tomorrow.plusDays(1).atStartOfDay(zone()).toInstant();
        int sent = 0;
        for (Appointment booked : appointments.findByStatusAndStartsAtBetween(Appointment.Status.BOOKED, from, to)) {
            Boolean done = tx.execute(status -> appointmentReminder(booked.getId()));
            sent += Boolean.TRUE.equals(done) ? 1 : 0;
        }
        return sent;
    }

    private boolean appointmentReminder(UUID appointmentId) {
        Appointment booked = appointments.findById(appointmentId).filter(a -> a.getStatus() == Appointment.Status.BOOKED).orElse(null);
        if (booked == null || reminderLogs.existsByKindAndRef(APPOINTMENT, booked.getId())) {
            return false;
        }
        Patient patient = booked.getPatient();
        if (!settings.findById(patient.getId()).map(ReminderSettings::isAppointmentReminders).orElse(false)) {
            return false;
        }
        ReminderLog entry = reminderLogs.saveAndFlush(new ReminderLog(patient.getId(), APPOINTMENT, booked.getId(), clock.instant()));
        String text = ReminderTexts.appointment(patient.getPreferredLanguage(), booked.getClinic().getName(),
                TIME.format(booked.getStartsAt().atZone(zone())));
        entry.delivered(send(patient, text, buttons(
                button(ReminderTexts.COMING_BUTTON, "a:" + entry.getId() + ":Y"),
                button(ReminderTexts.CANT_COME_BUTTON, "a:" + entry.getId() + ":N"))));
        return true;
    }

    /**
     * A booked visit with no visit started for the patient that day, two hours after its time, is a no-show. It goes
     * on the clinic's call list until someone records contact. Works for every clinic, whatever the patient chose.
     */
    public int detectNoShows() {
        Instant now = clock.instant();
        int found = 0;
        for (Appointment booked : appointments.findByStatusAndStartsAtBetween(Appointment.Status.BOOKED,
                now.minus(Duration.ofDays(7)), now.minus(NO_SHOW_AFTER))) {
            if (outcomes.existsById(booked.getId())) {
                continue;
            }
            LocalDate day = booked.getStartsAt().atZone(zone()).toLocalDate();
            if (encounters.existsByPatientIdAndStartedAtBetween(booked.getPatient().getId(), day.atStartOfDay(zone()).toInstant(),
                    day.plusDays(1).atStartOfDay(zone()).toInstant())) {
                continue;
            }
            outcomes.save(new AppointmentOutcome(booked.getId(), booked.getPatient().getId(), booked.getClinic().getId(),
                    booked.getStartsAt(), now));
            found++;
        }
        return found;
    }

    // ---- Answers --------------------------------------------------------------------------------------------

    /**
     * A tapped button. Only the patient's own linked chat can answer, and only reminders that were sent to that
     * patient; anything else is ignored. The buttons come off the message once it is answered.
     */
    public void handleCallback(String callbackId, String chatId, long messageId, String data) {
        Answer answer = tx.execute(status -> {
            Optional<Patient> patient = chatId == null || chatId.isBlank()
                    ? Optional.empty() : patients.findFirstByTelegramChatIndex(chatIndex.of(chatId));
            String[] parts = data == null ? new String[0] : data.split(":");
            if (patient.isEmpty() || parts.length != 3) {
                return new Answer(false, null);
            }
            UUID id;
            try {
                id = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                return new Answer(false, null);
            }
            return switch (parts[0] + parts[2]) {
                case "dT" -> answerDose(patient.get(), id, "TAKEN");
                case "dM" -> answerDose(patient.get(), id, "MISSED");
                case "aY" -> answerAppointment(patient.get(), id, true);
                case "aN" -> answerAppointment(patient.get(), id, false);
                default -> new Answer(false, null);
            };
        });
        bot.answerCallback(callbackId, answer == null || answer.message() == null ? null : shortNotice(answer.message()));
        if (answer != null && answer.recorded()) {
            bot.removeButtons(Long.parseLong(chatId), messageId);
            bot.sendMessage(Long.parseLong(chatId), answer.message());
        }
    }

    private static String shortNotice(String message) {
        int end = message.indexOf('.');
        return end > 0 ? message.substring(0, end + 1) : message;
    }

    /** Taken or Missed, from Telegram or the app. The first answer counts. */
    public Answer answerDose(Patient patient, UUID doseId, String response) {
        DoseLog dose = doses.findById(doseId)
                .filter(d -> d.getPatientId().equals(patient.getId()))
                .filter(d -> !d.getDay().isBefore(today().minusDays(ANSWER_DAYS)))
                .orElse(null);
        if (dose == null || !dose.answer(response, clock.instant())) {
            return new Answer(false, null);
        }
        doses.save(dose);
        String language = patient.getPreferredLanguage();
        if ("MISSED".equals(response)) {
            flagMissedDoses(patient);
            return new Answer(true, ReminderTexts.missed(language));
        }
        return new Answer(true, ReminderTexts.taken(language));
    }

    /** Two missed doses within three days: once a day at most, the clinic's call list and caregivers hear of it. */
    void flagMissedDoses(Patient patient) {
        LocalDate today = today();
        long missed = doses.countByPatientIdAndResponseAndDayGreaterThanEqual(patient.getId(), "MISSED", today.minusDays(MISSED_WINDOW_DAYS - 1L));
        if (missed < MISSED_TO_FLAG) {
            return;
        }
        UUID ref = UUID.nameUUIDFromBytes(("missed-doses:" + patient.getId() + ":" + today).getBytes(StandardCharsets.UTF_8));
        if (replies.findByPatientIdAndClientMessageId(patient.getId(), ref).isEmpty()) {
            PatientReply reply = new PatientReply(patient, "[Khabar dose reminders] Missed doses reported with the reminder buttons: "
                    + missed + " in the last " + MISSED_WINDOW_DAYS + " days.", clock.instant(), TriageLevel.REVIEW, "dose_reminder", ref);
            reply.markMissedDose();
            replies.save(reply);
        }
        caregivers.notify(patient, CaregiverAlerts.MISSED_DOSES, ref);
    }

    private Answer answerAppointment(Patient patient, UUID reminderId, boolean coming) {
        ReminderLog entry = reminderLogs.findById(reminderId)
                .filter(r -> APPOINTMENT.equals(r.getKind()) && r.getPatientId().equals(patient.getId()))
                .orElse(null);
        if (entry == null || !entry.answer(coming ? "COMING" : "CANT_COME", clock.instant())) {
            return new Answer(false, null);
        }
        reminderLogs.save(entry);
        String language = patient.getPreferredLanguage();
        if (coming) {
            return new Answer(true, ReminderTexts.coming(language));
        }
        Optional<Appointment> booked = appointments.findById(entry.getRef())
                .filter(a -> a.getStatus() == Appointment.Status.BOOKED)
                .filter(a -> a.getPatient().getId().equals(patient.getId()))
                .filter(a -> a.getStartsAt().isAfter(clock.instant()));
        booked.ifPresent(a -> a.cancel(clock.instant()));
        return new Answer(true, booked.isPresent() ? ReminderTexts.cancelled(language) : ReminderTexts.taken(language));
    }

    // ---- Sending --------------------------------------------------------------------------------------------

    /** With Telegram linked, the message carries its buttons; otherwise it goes the usual way and the app answers. */
    private boolean send(Patient patient, String text, Map<String, Object> markup) {
        if (bot.configured() && patient.getTelegramChatId() != null) {
            Messenger.Result result = bot.sendMessage(Long.parseLong(patient.getTelegramChatId()), text, markup);
            outbox.save(new OutboundMessage(patient.getId(), Messenger.Kind.NOTICE, "telegram", text, result, clock.instant()));
            return result.delivered();
        }
        return messages.send(patient, text, Messenger.Kind.NOTICE).delivered();
    }

    private static Map<String, Object> buttons(Map<String, Object> first, Map<String, Object> second) {
        return Map.of("inline_keyboard", List.of(List.of(first, second)));
    }

    private static Map<String, Object> button(String text, String data) {
        return Map.of("text", text, "callback_data", data);
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    private LocalDate today() {
        return clock.instant().atZone(zone()).toLocalDate();
    }
}
