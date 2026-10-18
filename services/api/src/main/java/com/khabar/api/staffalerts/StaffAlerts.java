package com.khabar.api.staffalerts;

import com.khabar.api.clinicops.ClinicOps;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.CallLists;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.messaging.Messenger;
import com.khabar.api.messaging.TelegramBotClient;
import com.khabar.api.scheduling.Appointment;
import com.khabar.api.scheduling.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff alerts and the morning briefing. Both go to the clinic's staff chat and say only how many patients wait,
 * how many are urgent and how many are past their acknowledgement time, with a link to the call list. Deciding
 * still happens in Khabar. Every attempt, including ones that could not be sent, is in the staff alert log.
 */
@Service
public class StaffAlerts {

    private static final Logger log = LoggerFactory.getLogger(StaffAlerts.class);
    static final Duration LINK_CODE_LIFETIME = Duration.ofMinutes(30);
    /** A new non-urgent change waits at least this long after the last alert; more urgent news never waits. */
    static final Duration MIN_GAP = Duration.ofMinutes(15);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Snapshot(int waiting, int urgent, int overdue) {
        String signature() {
            return "w" + waiting + "-u" + urgent + "-o" + overdue;
        }
    }

    public record LinkCode(String code, Instant expiresAt, String command) {
    }

    public record LogView(String kind, String outcome, String text, String detail, Instant at) {
    }

    private final StaffChannelRepository channels;
    private final StaffAlertLogRepository logs;
    private final CallLists callLists;
    private final ClinicOps clinicOps;
    private final ClinicRepository clinics;
    private final AppointmentRepository appointments;
    private final com.khabar.api.followup.FollowUpCases cases;
    private final com.khabar.api.identity.AppUserRepository users;
    private final TelegramBotClient bot;
    private final AdjustableClock clock;
    private final TransactionTemplate tx;
    private final String appUrl;

    public StaffAlerts(StaffChannelRepository channels, StaffAlertLogRepository logs, CallLists callLists, ClinicOps clinicOps,
                       ClinicRepository clinics, AppointmentRepository appointments, TelegramBotClient bot, AdjustableClock clock,
                       com.khabar.api.followup.FollowUpCases cases, com.khabar.api.identity.AppUserRepository users,
                       TransactionTemplate tx, @Value("${khabar.web.app-url:}") String appUrl) {
        this.channels = channels;
        this.logs = logs;
        this.callLists = callLists;
        this.clinicOps = clinicOps;
        this.clinics = clinics;
        this.appointments = appointments;
        this.cases = cases;
        this.users = users;
        this.bot = bot;
        this.clock = clock;
        this.tx = tx;
        this.appUrl = appUrl == null ? "" : appUrl.trim().replaceAll("/+$", "");
    }

    public StaffChannel channelFor(UUID clinicId) {
        return channels.findById(clinicId).orElseGet(() -> new StaffChannel(clinicId));
    }

    public boolean pendingCode(StaffChannel channel) {
        return channel.getLinkCodeExpiresAt() != null && channel.getLinkCodeExpiresAt().isAfter(clock.instant());
    }

    public boolean telegramConfigured() {
        return bot.configured();
    }

    // ---- Linking a chat --------------------------------------------------------------------------------------

    public LinkCode newLinkCode(AppUser actor) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        String code = "KB-" + randomCode(8);
        Instant expires = clock.instant().plus(LINK_CODE_LIFETIME);
        channel.newLinkCode(hash(code), expires, actor.getId());
        channels.save(channel);
        return new LinkCode(code, expires, "/staff " + code);
    }

    /**
     * Called by the Telegram webhook for "/staff CODE". Returns the clinic's name when the code was valid, so the
     * webhook can confirm in the chat.
     */
    public Optional<String> linkFromTelegram(String code, String chatId) {
        if (code == null || code.isBlank() || chatId == null || chatId.isBlank()) {
            return Optional.empty();
        }
        return tx.execute(status -> channels.findByLinkCodeHash(hash(code)).flatMap(channel -> {
            if (channel.getLinkCodeExpiresAt() == null || clock.instant().isAfter(channel.getLinkCodeExpiresAt())) {
                return Optional.<String>empty();
            }
            channel.link(chatId, clock.instant());
            channels.save(channel);
            return clinics.findById(channel.getClinicId()).map(Clinic::getName);
        }));
    }

    public void unlink(AppUser actor) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        channel.unlink();
        channels.save(channel);
    }

    public StaffChannel configure(AppUser actor, boolean alertsOn, Integer briefingHour, Integer handoverHour) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        channel.configure(alertsOn, briefingHour, handoverHour);
        return channels.save(channel);
    }

    // ---- What is said ----------------------------------------------------------------------------------------

    public Snapshot snapshot(UUID clinicId, Instant now) {
        CallLists.CallList list = callLists.build(clinicId, now, false);
        int urgent = (int) list.items().stream().filter(i -> i.level() == TriageLevel.RED).count();
        int overdue = (int) list.items().stream().filter(i -> i.followUpCase() != null && i.followUpCase().overdue()).count();
        return new Snapshot(list.items().size(), urgent, overdue);
    }

    String alertText(String clinicName, Snapshot s) {
        StringBuilder text = new StringBuilder("Khabar · ").append(clinicName).append(": ")
                .append(s.waiting()).append(s.waiting() == 1 ? " patient needs" : " patients need").append(" a call");
        List<String> parts = new java.util.ArrayList<>();
        if (s.urgent() > 0) parts.add(s.urgent() + " urgent");
        if (s.overdue() > 0) parts.add(s.overdue() + " past the acknowledgement time");
        if (!parts.isEmpty()) text.append(" (").append(String.join(", ", parts)).append(")");
        text.append('.');
        appendLink(text, "Open the call list");
        return text.toString();
    }

    public String briefingText(UUID clinicId) {
        Instant now = clock.instant();
        String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("your clinic");
        Snapshot s = snapshot(clinicId, now);
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        int booked = appointments.findByClinicIdAndStatusAndStartsAtBetweenOrderByStartsAt(clinicId, Appointment.Status.BOOKED, from, to).size();
        ClinicOps.Coverage cover = clinicOps.coverageToday(clinicId);

        StringBuilder text = new StringBuilder("Good morning, ").append(clinicName).append(". Today:\n");
        text.append("• Call list: ").append(s.waiting()).append(s.waiting() == 1 ? " patient" : " patients");
        if (s.urgent() > 0 || s.overdue() > 0) {
            text.append(" (").append(s.urgent()).append(" urgent, ").append(s.overdue()).append(" past the acknowledgement time)");
        }
        text.append('\n');
        text.append("• Appointments: ").append(booked).append(" booked\n");
        if (cover.onDuty() != null) {
            text.append("• On duty: ").append(cover.onDuty());
            if (cover.backup() != null) text.append(" (backup: ").append(cover.backup()).append(')');
            text.append('\n');
        }
        if (cover.warning() != null) {
            text.append("• ").append(cover.warning()).append('\n');
        }
        String body = text.toString().stripTrailing();
        StringBuilder withLink = new StringBuilder(body);
        appendLink(withLink, "Open Khabar");
        return withLink.toString();
    }

    private void appendLink(StringBuilder text, String label) {
        if (!appUrl.isEmpty()) {
            text.append("\n").append(label).append(": ").append(appUrl).append("/home");
        }
    }

    // ---- Sending ---------------------------------------------------------------------------------------------

    public LogView sendTest(AppUser actor) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        String text = "Khabar · " + actor.getClinic().getName() + ": test message from " + actor.getDisplayName()
                + ". Staff alerts will look like this: counts and a link only, never a patient's name.";
        return view(deliver(channel, "TEST", text));
    }

    /**
     * End-of-shift handover: open cases by state, how many have no owner or are past the acknowledgement time,
     * and how many each colleague owns. Staff names appear; patients never do.
     */
    public String handoverText(UUID clinicId) {
        String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("your clinic");
        Instant now = clock.instant();
        var settings = clinicOps.settingsFor(clinicId);
        List<com.khabar.api.followup.FollowUpCase> open = cases.openCases(clinicId);
        long unassigned = open.stream().filter(c -> c.getOwnerId() == null).count();
        long overdue = open.stream().filter(c -> cases.view(c, settings, now).overdue()).count();
        long urgent = open.stream().filter(c -> c.getLevel() == TriageLevel.RED).count();
        java.util.Map<String, Long> byOwner = new java.util.TreeMap<>();
        open.stream().filter(c -> c.getOwnerId() != null).forEach(c ->
                byOwner.merge(users.findById(c.getOwnerId()).map(AppUser::getDisplayName).orElse("Former staff member"), 1L, Long::sum));
        StringBuilder text = new StringBuilder("Handover · ").append(clinicName).append('\n');
        text.append("• Open cases: ").append(open.size());
        if (urgent > 0) text.append(" (").append(urgent).append(" urgent)");
        text.append('\n');
        text.append("• No owner yet: ").append(unassigned).append('\n');
        text.append("• Past the acknowledgement time: ").append(overdue).append('\n');
        if (!byOwner.isEmpty()) {
            text.append("• Owners: ");
            text.append(String.join(", ", byOwner.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).toList()));
            text.append('\n');
        }
        text.append("Please hand over or reassign anything you cannot finish.");
        appendLink(text, "Open the call list");
        return text.toString();
    }

    public LogView sendHandoverNow(AppUser actor) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        return view(deliver(channel, "HANDOVER", handoverText(channel.getClinicId())));
    }

    public LogView sendBriefingNow(AppUser actor) {
        StaffChannel channel = channelFor(actor.getClinic().getId());
        return view(deliver(channel, "BRIEFING", briefingText(channel.getClinicId())));
    }

    /** One pass over every clinic with alerts on. Returns how many messages were attempted. */
    public int checkAll() {
        int attempted = 0;
        for (StaffChannel candidate : channels.findByAlertsOnTrue()) {
            try {
                Integer n = tx.execute(status -> checkClinic(candidate.getClinicId()));
                attempted += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("Staff alert check failed for a clinic: {}", e.getClass().getSimpleName());
            }
        }
        return attempted;
    }

    int checkClinic(UUID clinicId) {
        StaffChannel channel = channels.findById(clinicId).orElse(null);
        if (channel == null || !channel.isAlertsOn()) {
            return 0;
        }
        Instant now = clock.instant();
        int attempted = 0;
        LocalDateTime local = LocalDateTime.now(clock);
        if (channel.getBriefingHour() != null && local.getHour() >= channel.getBriefingHour()
                && !local.toLocalDate().equals(channel.getLastBriefingDate())) {
            deliver(channel, "BRIEFING", briefingText(clinicId));
            channel.briefed(local.toLocalDate());
            attempted++;
        }
        if (channel.getHandoverHour() != null && local.getHour() >= channel.getHandoverHour()
                && !local.toLocalDate().equals(channel.getLastHandoverDate())) {
            deliver(channel, "HANDOVER", handoverText(clinicId));
            channel.handedOver(local.toLocalDate());
            attempted++;
        }
        Snapshot s = snapshot(clinicId, now);
        if (s.waiting() == 0) {
            channel.cleared();
        } else if (!s.signature().equals(channel.getLastSignature())) {
            boolean moreUrgent = s.urgent() > channel.getLastUrgent();
            boolean gapPassed = channel.getLastAlertAt() == null || !now.isBefore(channel.getLastAlertAt().plus(MIN_GAP));
            if (moreUrgent || gapPassed) {
                String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("your clinic");
                deliver(channel, "ALERT", alertText(clinicName, s));
                channel.alerted(s.signature(), s.urgent(), now);
                attempted++;
            }
        }
        channels.save(channel);
        return attempted;
    }

    private StaffAlertLog deliver(StaffChannel channel, String kind, String text) {
        Instant now = clock.instant();
        String outcome;
        String detail = null;
        if (channel.getChatId() == null) {
            outcome = "NOT_SENT";
            detail = "No staff chat is linked yet.";
        } else if (!bot.configured()) {
            outcome = "NOT_SENT";
            detail = "Telegram is not configured on this server, so the message was only recorded here.";
        } else {
            Messenger.Result result;
            try {
                result = bot.sendMessage(Long.parseLong(channel.getChatId()), text);
            } catch (NumberFormatException e) {
                result = Messenger.Result.failed("The linked chat ID is not valid.");
            }
            outcome = result.delivered() ? "SENT" : "FAILED";
            detail = result.delivered() ? null : result.error();
        }
        return logs.save(new StaffAlertLog(channel.getClinicId(), kind, text, outcome, detail, now));
    }

    public List<LogView> recent(UUID clinicId) {
        return logs.findByClinicIdOrderByIdDesc(clinicId, PageRequest.of(0, 20)).stream().map(StaffAlerts::view).toList();
    }

    private static LogView view(StaffAlertLog l) {
        return new LogView(l.getKind(), l.getOutcome(), l.getText(), l.getDetail(), l.getAt());
    }

    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("khabar-staff-link:" + code.trim().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
