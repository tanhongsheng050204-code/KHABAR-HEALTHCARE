package com.khabar.api.ops;

import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.pathways.Pathways;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.PhoneIndex;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bring a clinic's patient list in from a spreadsheet saved as CSV: name, IC, phone, language and, optionally, a
 * condition. A preview checks every row first (IC format, phone, language, duplicates in the file and in the
 * clinic) and nothing is saved until the admin confirms; then only the rows without problems are added.
 */
@RestController
public class PatientImport {

    static final int MAX_ROWS = 500;
    static final int MAX_CSV = 512 * 1024;
    static final int MAX_XLSX = 2 * 1024 * 1024;

    /** Either CSV text, or an Excel .xlsx file as base64. */
    public record Input(String csv, String xlsxBase64) {
    }

    /** status: OK, ERROR or DUPLICATE. */
    public record Row(int line, String fullName, String icMasked, String phone, String language, String condition, String status, List<String> problems) {
    }

    public record Result(boolean saved, int ok, int problems, List<Row> rows) {
    }

    private final CurrentUser currentUser;
    private final ClinicStaffAccess staffAccess;
    private final PatientRepository patients;
    private final PhoneIndex phoneIndex;
    private final Pathways pathways;
    private final PatientGraphSync graphSync;

    public PatientImport(CurrentUser currentUser, ClinicStaffAccess staffAccess, PatientRepository patients, PhoneIndex phoneIndex,
                         Pathways pathways, PatientGraphSync graphSync) {
        this.currentUser = currentUser;
        this.staffAccess = staffAccess;
        this.patients = patients;
        this.phoneIndex = phoneIndex;
        this.pathways = pathways;
        this.graphSync = graphSync;
    }

    @PostMapping("/api/clinic/patients/import")
    @Transactional
    public Result importPatients(@RequestBody Input input, @RequestParam(defaultValue = "true") boolean dryRun,
                                 @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (actor.getClinic() == null || !staffAccess.canManageStaff(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors and clinic administrators import patients.");
        }
        List<List<String>> table;
        if (input.xlsxBase64() != null && !input.xlsxBase64().isBlank()) {
            byte[] xlsx;
            try {
                xlsx = java.util.Base64.getDecoder().decode(input.xlsxBase64().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The Excel file could not be read.");
            }
            if (xlsx.length > MAX_XLSX) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is too large; import up to 500 patients at a time.");
            }
            try {
                table = XlsxReader.read(xlsx);
            } catch (java.io.IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        } else {
            String csv = input.csv() == null ? "" : input.csv();
            if (csv.length() > MAX_CSV) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is too large; import up to 500 patients at a time.");
            }
            table = parse(csv);
        }
        if (table.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        Map<String, Integer> col = header(table.get(0));
        if (!col.containsKey("name") || !col.containsKey("phone")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The first row must name the columns: name, ic, phone, language, condition.");
        }
        if (table.size() - 1 > MAX_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import up to 500 patients at a time.");
        }

        List<Patient> existing = patients.findByClinicId(actor.getClinic().getId());
        Set<String> clinicIcs = new HashSet<>();
        existing.forEach(p -> { if (p.getIcNumber() != null) clinicIcs.add(digits(p.getIcNumber())); });
        Set<String> filePhones = new HashSet<>();
        Set<String> fileIcs = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        List<Object[]> toSave = new ArrayList<>();
        for (int i = 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            String name = cell(cells, col, "name");
            String ic = cell(cells, col, "ic");
            String phone = cell(cells, col, "phone");
            String language = language(cell(cells, col, "language"));
            String condition = condition(cell(cells, col, "condition"));
            List<String> problems = new ArrayList<>();
            String status = "OK";
            if (name.isBlank() || name.length() > 200) problems.add("Name is missing or too long.");
            if (!ic.isBlank() && !ic.matches("\\d{12}|\\d{6}-\\d{2}-\\d{4}")) problems.add("IC must have 12 digits, with or without hyphens.");
            if (digits(phone).length() < 9 || digits(phone).length() > 13) problems.add("Phone number does not look right.");
            if (language == null) problems.add("Language must be BM, English, Chinese or Tamil.");
            if (condition == null) problems.add("Condition must be diabetes, hypertension or post-op, or left empty.");
            if (!problems.isEmpty()) {
                status = "ERROR";
            } else {
                String index = phoneIndex.of(phone);
                if (!filePhones.add(index)) { problems.add("The same phone number is earlier in this file."); status = "DUPLICATE"; }
                else if (!patients.findByPhoneIndex(index).stream().filter(p -> p.getClinic().getId().equals(actor.getClinic().getId())).toList().isEmpty()) {
                    problems.add("A patient with this phone number is already at the clinic."); status = "DUPLICATE";
                }
                if (!ic.isBlank()) {
                    if (clinicIcs.contains(digits(ic))) { problems.add("A patient with this IC is already at the clinic."); status = "DUPLICATE"; }
                    else if (!fileIcs.add(digits(ic))) { problems.add("The same IC is earlier in this file."); status = "DUPLICATE"; }
                }
            }
            rows.add(new Row(i + 1, name, ic.isBlank() ? null : "******-**-" + digits(ic).substring(Math.max(0, digits(ic).length() - 4)),
                    phone, language, condition == null || condition.isEmpty() ? null : condition, status, problems));
            if (status.equals("OK")) {
                toSave.add(new Object[]{name.strip(), ic.isBlank() ? null : ic.strip(), phone.strip(), language, condition});
            }
        }
        int problemCount = (int) rows.stream().filter(r -> !r.status().equals("OK")).count();
        if (dryRun) {
            return new Result(false, toSave.size(), problemCount, rows);
        }
        for (Object[] r : toSave) {
            Patient patient = patients.save(new Patient(actor.getClinic(), null, (String) r[0], (String) r[1], (String) r[2], (String) r[3]));
            String condition = (String) r[4];
            if (condition != null && !condition.isEmpty()) {
                pathways.start(patient, actor, new Pathways.Input(condition, null, null, null, null, null, null));
            }
            graphSync.changed(patient.getId());
        }
        return new Result(true, toSave.size(), problemCount, rows);
    }

    private static Map<String, Integer> header(List<String> cells) {
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String h = cells.get(i).trim().toLowerCase(Locale.ROOT);
            if (h.contains("name") || h.equals("nama")) col.putIfAbsent("name", i);
            else if (h.equals("ic") || h.contains("ic ") || h.contains("mykad") || h.startsWith("ic")) col.putIfAbsent("ic", i);
            else if (h.contains("phone") || h.contains("telefon") || h.contains("mobile")) col.putIfAbsent("phone", i);
            else if (h.contains("lang") || h.contains("bahasa")) col.putIfAbsent("language", i);
            else if (h.contains("condition") || h.contains("pathway") || h.contains("penyakit")) col.putIfAbsent("condition", i);
        }
        return col;
    }

    private static String cell(List<String> cells, Map<String, Integer> col, String key) {
        Integer i = col.get(key);
        return i == null || i >= cells.size() ? "" : cells.get(i).trim();
    }

    /** null for an unknown language; English when empty. */
    static String language(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "", "en", "english", "inggeris" -> "en";
            case "ms", "bm", "malay", "melayu", "bahasa melayu", "bahasa" -> "ms";
            case "zh", "chinese", "mandarin", "cina", "中文" -> "zh";
            case "ta", "tamil", "தமிழ்" -> "ta";
            default -> null;
        };
    }

    /** "" for none, null for an unknown condition. */
    static String condition(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "" -> "";
            case "diabetes", "dm", "t2dm", "kencing_manis" -> Pathways.DIABETES;
            case "hypertension", "htn", "high_blood_pressure", "darah_tinggi" -> Pathways.HYPERTENSION;
            case "post_op", "postop", "surgery", "after_operation" -> Pathways.POST_OP;
            default -> null;
        };
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /** RFC 4180-style CSV: commas, quoted fields, doubled quotes, CRLF or LF. A leading byte-order mark is ignored. */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
