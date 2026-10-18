package com.khabar.api.encounters;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A second, independent check on the patient's take-home plan before it is sent. The agents service writes the
 * plan from fixed, reviewed phrases; this check does not trust that. Every medicine in the plan must be one the
 * doctor prescribed, every prescribed medicine must be in the plan, and every number in the plan must come
 * from the prescription or the follow-up interval. Anything else holds the plan for the doctor.
 *
 * It is a written rule, not an AI checking an AI: it can hold a correct plan (a number it does not recognise),
 * but it never lets an unrecognised medicine name or number through.
 */
public final class SummaryCheck {

    /** The emergency number in the fixed safety wording is always allowed. */
    private static final Set<String> ALWAYS_ALLOWED = Set.of("999");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern BULLET = Pattern.compile("^\\s*[•\\-*]\\s*(.+?)\\s*:\\s*(.*)$");

    private SummaryCheck() {
    }

    public static List<String> check(String text, List<PrescriptionLine> prescription, Double followUpWeeks) {
        List<String> problems = new ArrayList<>();
        if (text == null || text.isBlank()) {
            problems.add("The plan is empty.");
            return problems;
        }
        Set<String> allowed = allowedNumbers(prescription, followUpWeeks);
        Set<String> mentioned = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            Matcher bullet = BULLET.matcher(line);
            if (!bullet.matches()) {
                continue;
            }
            String label = bullet.group(1);
            List<PrescriptionLine> sameName = prescription.stream().filter(rx -> names(label, rx)).toList();
            // Two lines of one medicine differ by strength, which the label states.
            PrescriptionLine match = sameName.stream()
                    .filter(rx -> rx.getStrengthMg() != null && label.contains(normalise(String.valueOf(rx.getStrengthMg()))))
                    .findFirst()
                    .orElse(sameName.isEmpty() ? null : sameName.get(0));
            if (match == null) {
                problems.add("“" + label + "” is in the plan but not in the prescription.");
            } else {
                mentioned.add(key(match));
            }
        }
        for (PrescriptionLine rx : prescription) {
            if (!mentioned.contains(key(rx))) {
                problems.add("“" + display(rx) + "” is in the prescription but missing from the plan.");
            }
        }
        Set<String> unknown = new LinkedHashSet<>();
        Matcher numbers = NUMBER.matcher(text);
        while (numbers.find()) {
            String n = normalise(numbers.group());
            if (!allowed.contains(n)) {
                unknown.add(numbers.group());
            }
        }
        for (String n : unknown) {
            problems.add("The number " + n + " in the plan does not come from the prescription or the follow-up date.");
        }
        return problems;
    }

    static Set<String> allowedNumbers(List<PrescriptionLine> prescription, Double followUpWeeks) {
        Set<String> allowed = new LinkedHashSet<>(ALWAYS_ALLOWED);
        for (PrescriptionLine rx : prescription) {
            add(allowed, rx.getStrengthMg());
            add(allowed, rx.getUnitsPerDose());
            if (rx.getTimesPerDay() != null) {
                add(allowed, rx.getTimesPerDay().doubleValue());
            }
            for (String part : List.of(rx.getRaw() == null ? "" : rx.getRaw(), rx.getName() == null ? "" : rx.getName())) {
                Matcher m = NUMBER.matcher(part);
                while (m.find()) {
                    allowed.add(normalise(m.group()));
                }
            }
        }
        if (followUpWeeks != null) {
            add(allowed, followUpWeeks);
            add(allowed, Math.round(followUpWeeks * 10) / 10.0);
            add(allowed, (double) Math.round(followUpWeeks * 7));
        }
        return allowed;
    }

    /** The plan labels a medicine "name" or "name strength mg"; compare without case or spacing. */
    private static boolean names(String label, PrescriptionLine rx) {
        if (rx.getName() == null) {
            return false;
        }
        String l = squash(label);
        String name = squash(rx.getName());
        return l.equals(name) || l.startsWith(name);
    }

    private static String key(PrescriptionLine rx) {
        return squash(rx.getName() == null ? String.valueOf(rx.getRaw()) : rx.getName()) + "|" + rx.getStrengthMg();
    }

    private static String display(PrescriptionLine rx) {
        return rx.getName() == null ? String.valueOf(rx.getRaw()) : rx.getName();
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static void add(Set<String> allowed, Double value) {
        if (value != null) {
            allowed.add(normalise(String.valueOf(value)));
        }
    }

    static String normalise(String number) {
        try {
            return new BigDecimal(number.replace(',', '.')).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return number;
        }
    }
}
