package com.khabar.api.intake;

import com.khabar.api.patients.Patient;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes a patient's own identity from free text they typed, before it leaves this service.
 * This side knows the patient's real name, IC and phone, so it can remove them exactly;
 * the AI service also scrubs anything that looks like an IC or phone number as a second lock.
 */
final class Redactor {

    private static final Pattern ANY_IC = Pattern.compile("(?<!\\d)\\d{6}-?\\d{2}-?\\d{4}(?!\\d)");
    private static final Pattern ANY_MOBILE = Pattern.compile("(?<![\\d+])(?:\\+?60[\\s-]?|0)1\\d[\\s-]?\\d{3,4}[\\s-]?\\d{4}(?!\\d)");
    private static final Set<String> NAME_CONNECTORS = Set.of("bin", "binti", "bt", "bte", "al", "ap", "a/l", "a/p", "s/o", "d/o", "anak");

    private Redactor() {
    }

    static String redact(String text, Patient patient) {
        if (text == null) {
            return null;
        }
        String out = ANY_IC.matcher(text).replaceAll("[IC]");
        out = ANY_MOBILE.matcher(out).replaceAll("[PHONE]");
        if (patient.getPhone() != null) {
            out = out.replace(patient.getPhone(), "[PHONE]");
        }
        for (String part : patient.getFullName().split("\\s+")) {
            if (part.length() >= 3 && !NAME_CONNECTORS.contains(part.toLowerCase())) {
                out = Pattern.compile("\\b" + Pattern.quote(part) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(out).replaceAll(Matcher.quoteReplacement("[NAME]"));
            }
        }
        return out;
    }
}
