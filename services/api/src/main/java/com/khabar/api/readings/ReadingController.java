package com.khabar.api.readings;

import com.fasterxml.jackson.databind.JsonNode;
import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.config.AdjustableClock;
import com.khabar.api.followup.TriageLevel;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientAccessPolicy;
import com.khabar.api.patients.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * F4: home blood pressure and blood sugar. Readings come from the patient or a caregiver in the app,
 * or from a linked device through Favoriot's HTTP forwarding. A worrying one goes on the call list.
 */
@RestController
public class ReadingController {

    private static final Logger log = LoggerFactory.getLogger(ReadingController.class);

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final ReadingRepository readings;
    private final DeviceLinkRepository devices;
    private final AuditLog auditLog;
    private final AdjustableClock clock;
    private final String deviceSecret;
    private final PatientGraphSync graphSync;
    private final ClinicStaffAccess staffAccess;

    public ReadingController(CurrentUser currentUser, PatientRepository patients, PatientAccessPolicy policy, ReadingRepository readings,
                             DeviceLinkRepository devices, AuditLog auditLog, AdjustableClock clock,
                             @Value("${khabar.favoriot.device-secret:}") String deviceSecret, PatientGraphSync graphSync,
                             ClinicStaffAccess staffAccess) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.policy = policy;
        this.readings = readings;
        this.devices = devices;
        this.auditLog = auditLog;
        this.clock = clock;
        this.deviceSecret = deviceSecret;
        this.graphSync = graphSync;
        this.staffAccess = staffAccess;
    }

    public record ReadingRequest(Double glucose, Integer systolic, Integer diastolic) {
    }

    public record ReadingView(UUID id, Reading.Kind kind, String description, TriageLevel level, String source, Instant measuredAt) {
        static ReadingView of(Reading r) {
            return new ReadingView(r.getId(), r.getKind(), r.getDescription(), r.getLevel(), r.getSource(), r.getMeasuredAt());
        }
    }

    public record DeviceRequest(String deviceId) {
    }

    @PostMapping("/api/patients/{patientId}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReadingView record(@PathVariable UUID patientId, @RequestBody ReadingRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = allowed(user, patientId);
        String source = switch (user.getRole()) {
            case PATIENT -> "patient";
            case CAREGIVER -> "caregiver";
            case DOCTOR, NURSE, CLINIC_ADMIN -> "clinic";
        };
        Reading saved = readings.save(build(patient, request.glucose(), request.systolic(), request.diastolic(), source));
        graphSync.changed(patient.getId());
        return ReadingView.of(saved);
    }

    @GetMapping("/api/patients/{patientId}/readings")
    @Transactional
    public List<ReadingView> list(@PathVariable UUID patientId, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = allowed(user, patientId);
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_READINGS);
        }
        return readings.findTop30ByPatientIdOrderByMeasuredAtDesc(patient.getId()).stream().map(ReadingView::of).toList();
    }

    @PutMapping("/api/patients/{patientId}/device")
    @Transactional
    public DeviceRequest linkDevice(@PathVariable UUID patientId, @RequestBody DeviceRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = currentUser.from(jwt);
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!staffAccess.hasRole(doctor, ClinicStaffRole.DOCTOR) || doctor.getClinic() == null
                || !doctor.getClinic().getId().equals(patient.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Devices are linked by the patient's clinic.");
        }
        String deviceId = request.deviceId() == null ? "" : request.deviceId().trim();
        if (deviceId.isEmpty() || deviceId.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the device's Favoriot developer id.");
        }
        devices.findById(deviceId).ifPresent(existing -> {
            if (!existing.getPatient().getId().equals(patient.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That device already sends readings for another patient.");
            }
        });
        devices.save(new DeviceLink(deviceId, patient, doctor.getId(), clock.instant()));
        return new DeviceRequest(deviceId);
    }

    /**
     * Favoriot's HTTP forwarding. Set a header X-Khabar-Device-Secret on the forwarding rule; the body is
     * Favoriot's stream JSON, {"device_developer_id": "...", "data": {"glucose": 5.4}} or {"systolic": 130,
     * "diastolic": 85}. Values may be numbers or numeric strings. Unknown devices are acknowledged and ignored.
     */
    @PostMapping(path = "/api/webhooks/favoriot", produces = MediaType.TEXT_PLAIN_VALUE)
    @Transactional
    public String fromDevice(@RequestBody JsonNode body, @RequestHeader(value = "X-Khabar-Device-Secret", required = false) String secret) {
        if (deviceSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Device readings are not configured.");
        }
        if (secret == null || !MessageDigest.isEqual(deviceSecret.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad device secret.");
        }
        String deviceId = body.path("device_developer_id").asText("");
        JsonNode data = body.path("data");
        return devices.findById(deviceId).map(link -> {
            readings.save(build(link.getPatient(), number(data, "glucose"), whole(data, "systolic"), whole(data, "diastolic"), "favoriot"));
            graphSync.changed(link.getPatient().getId());
            return "OK";
        }).orElseGet(() -> {
            log.info("Reading from a device not linked to any patient; ignored.");
            return "IGNORED";
        });
    }

    private Reading build(Patient patient, Double glucose, Integer systolic, Integer diastolic, String source) {
        Instant now = clock.instant();
        if (glucose != null && systolic == null && diastolic == null) {
            if (glucose < 0.5 || glucose > 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blood sugar should be in mmol/L, between 0.5 and 40.");
            }
            return Reading.glucose(patient, glucose, now, now, source);
        }
        if (glucose == null && systolic != null && diastolic != null) {
            if (systolic < 50 || systolic > 300 || diastolic < 30 || diastolic > 200 || systolic <= diastolic) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That blood pressure doesn't look right. Check the numbers.");
            }
            return Reading.bloodPressure(patient, systolic, diastolic, now, now, source);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send either a blood sugar, or both blood pressure numbers.");
    }

    private Patient allowed(AppUser user, UUID patientId) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return patient;
    }

    private static Double number(JsonNode data, String field) {
        JsonNode node = data.path(field);
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            return node.isTextual() ? Double.valueOf(node.asText().trim()) : null;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is not a number.");
        }
    }

    private static Integer whole(JsonNode data, String field) {
        Double value = number(data, field);
        return value == null ? null : (int) Math.round(value);
    }
}
