package com.khabar.api.insights;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.PatientReply;
import com.khabar.api.followup.PatientReplyRepository;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.labs.Labs;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.readings.Reading;
import com.khabar.api.readings.ReadingRepository;
import com.khabar.api.reminders.DoseLog;
import com.khabar.api.reminders.DoseLogRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * How a patient is doing over time: home readings, dose answers and replies over the last few weeks, each with a
 * direction. A direction compares the last 7 days with the 7 before; with too few readings in either it says so
 * rather than guess. These are counts and averages of what the patient sent, not a clinical assessment.
 */
@Service
public class Trends {

    public static final String IMPROVING = "IMPROVING";
    public static final String WORSENING = "WORSENING";
    public static final String FLAT = "FLAT";
    public static final String NOT_ENOUGH = "NOT_ENOUGH_DATA";

    static final double GLUCOSE_STEP = 0.5;
    static final double SYSTOLIC_STEP = 5;
    static final double DOSE_STEP = 0.15;

    public record Point(Instant at, double value) {
    }

    /** recent and earlier are the 7-day averages compared; outOfTarget counts readings above target in the window. */
    public record Series(String key, String label, String unit, Double target, List<Point> points, String direction,
                         Double recent, Double earlier, int outOfTarget) {
    }

    public record DayDoses(LocalDate day, int sent, int taken, int missed) {
    }

    public record DayReplies(LocalDate day, int replies, int needingCall, String worst) {
    }

    public record Summary(int days, List<Series> series, List<DayDoses> doses, String doseDirection, List<DayReplies> replies,
                          List<Labs.LabView> labs, String overall, List<String> notes) {

        /** What a caregiver may see: readings and dose answers, which the patient shares with them; not labs or reply triage. */
        public Summary forCaregiver() {
            return new Summary(days, series, doses, doseDirection, List.of(), List.of(), overall, notes);
        }
    }

    private final ReadingRepository readings;
    private final DoseLogRepository doses;
    private final PatientReplyRepository replies;
    private final Pathways pathways;
    private final Labs labs;
    private final AdjustableClock clock;

    public Trends(ReadingRepository readings, DoseLogRepository doses, PatientReplyRepository replies, Pathways pathways, Labs labs,
                  AdjustableClock clock) {
        this.readings = readings;
        this.doses = doses;
        this.replies = replies;
        this.pathways = pathways;
        this.labs = labs;
        this.clock = clock;
    }

    public Summary summary(UUID patientId, int days) {
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(days));
        Pathways.Targets targets = pathways.targets(patientId);
        List<Reading> window = readings.findByPatientIdOrderByMeasuredAtAsc(patientId).stream()
                .filter(r -> !r.getMeasuredAt().isBefore(from) && !r.getMeasuredAt().isAfter(now))
                .toList();

        List<Series> series = new ArrayList<>();
        List<Reading> glucose = window.stream().filter(r -> r.getKind() == Reading.Kind.GLUCOSE && r.getGlucose() != null).toList();
        if (!glucose.isEmpty()) {
            series.add(series("GLUCOSE", "Blood sugar", "mmol/L", targets.glucoseMax(), glucose, Reading::getGlucose, GLUCOSE_STEP, now));
        }
        List<Reading> pressure = window.stream().filter(r -> r.getKind() == Reading.Kind.BLOOD_PRESSURE && r.getSystolic() != null).toList();
        if (!pressure.isEmpty()) {
            series.add(series("SYSTOLIC", "Blood pressure (top)", "mmHg", targets.systolicMax() == null ? null : targets.systolicMax().doubleValue(),
                    pressure, r -> r.getSystolic().doubleValue(), SYSTOLIC_STEP, now));
            series.add(series("DIASTOLIC", "Blood pressure (bottom)", "mmHg", targets.diastolicMax() == null ? null : targets.diastolicMax().doubleValue(),
                    pressure, r -> r.getDiastolic().doubleValue(), SYSTOLIC_STEP, now));
        }

        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate firstDay = today.minusDays(days - 1L);
        List<DoseLog> doseRows = doses.findByPatientIdAndDayGreaterThanEqualOrderByDayAscSentAtAsc(patientId, firstDay);
        Map<LocalDate, int[]> perDay = new TreeMap<>();
        for (DoseLog d : doseRows) {
            int[] c = perDay.computeIfAbsent(d.getDay(), k -> new int[3]);
            c[0]++;
            if ("TAKEN".equals(d.getResponse())) c[1]++;
            if ("MISSED".equals(d.getResponse())) c[2]++;
        }
        List<DayDoses> doseDays = perDay.entrySet().stream().map(e -> new DayDoses(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        String doseDirection = doseDirection(doseRows, today);

        Map<LocalDate, List<PatientReply>> repliesByDay = new TreeMap<>();
        for (PatientReply r : replies.findByPatientIdOrderByReceivedAtAsc(patientId)) {
            if (!r.getReceivedAt().isBefore(from) && !r.getReceivedAt().isAfter(now)) {
                repliesByDay.computeIfAbsent(LocalDate.ofInstant(r.getReceivedAt(), zone), k -> new ArrayList<>()).add(r);
            }
        }
        List<DayReplies> replyDays = repliesByDay.entrySet().stream().map(e -> new DayReplies(e.getKey(), e.getValue().size(),
                (int) e.getValue().stream().filter(PatientReply::needsACall).count(),
                e.getValue().stream().map(PatientReply::getLevel).min(Enum::compareTo).map(TriageLevel::name).orElse(null))).toList();

        List<String> directions = new ArrayList<>(series.stream().filter(s -> !s.key().equals("DIASTOLIC")).map(Series::direction).toList());
        directions.add(doseDirection);
        String overall = directions.contains(WORSENING) ? WORSENING : directions.contains(IMPROVING) ? IMPROVING
                : directions.contains(FLAT) ? FLAT : NOT_ENOUGH;
        List<String> notes = new ArrayList<>();
        if (series.isEmpty()) {
            notes.add("No home readings in this period.");
        }
        if (doseRows.isEmpty()) {
            notes.add("No dose reminders in this period.");
        }
        return new Summary(days, series, doseDays, doseDirection, replyDays, labs.views(patientId), overall, notes);
    }

    private static Series series(String key, String label, String unit, Double target, List<Reading> rows,
                                 Function<Reading, Double> value, double step, Instant now) {
        List<Point> points = rows.stream().map(r -> new Point(r.getMeasuredAt(), value.apply(r))).toList();
        Double recent = average(points, now.minus(Duration.ofDays(7)), now);
        Double earlier = average(points, now.minus(Duration.ofDays(14)), now.minus(Duration.ofDays(7)));
        String direction = recent == null || earlier == null ? NOT_ENOUGH
                : recent - earlier > step ? WORSENING : earlier - recent > step ? IMPROVING : FLAT;
        int out = target == null ? 0 : (int) points.stream().filter(p -> p.value() > target).count();
        return new Series(key, label, unit, target, points, direction, round(recent), round(earlier), out);
    }

    /** The average of points in [from, to), or null with fewer than two of them. */
    private static Double average(List<Point> points, Instant from, Instant to) {
        List<Double> in = points.stream().filter(p -> !p.at().isBefore(from) && p.at().isBefore(to)).map(Point::value).toList();
        return in.size() < 2 ? null : in.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Share of doses answered Taken: last 7 days against the 7 before. Unanswered counts as not taken. */
    private static String doseDirection(List<DoseLog> rows, LocalDate today) {
        Double recent = takenShare(rows, today.minusDays(6), today);
        Double earlier = takenShare(rows, today.minusDays(13), today.minusDays(7));
        if (recent == null || earlier == null) {
            return NOT_ENOUGH;
        }
        return recent - earlier > DOSE_STEP ? IMPROVING : earlier - recent > DOSE_STEP ? WORSENING : FLAT;
    }

    private static Double takenShare(List<DoseLog> rows, LocalDate from, LocalDate to) {
        List<DoseLog> in = rows.stream().filter(d -> !d.getDay().isBefore(from) && !d.getDay().isAfter(to)).toList();
        return in.size() < 3 ? null : in.stream().filter(d -> "TAKEN".equals(d.getResponse())).count() / (double) in.size();
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
