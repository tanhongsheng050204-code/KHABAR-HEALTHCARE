package com.khabar.api.scheduling;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** B1: patients book their own visit from the clinic's open slots; doctors see the day's list. */
@RestController
public class BookingController {

    private static final int MAX_REASON = 500;

    private final CurrentUser currentUser;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final AdjustableClock clock;
    private final ClinicStaffAccess staffAccess;

    public BookingController(CurrentUser currentUser, PatientRepository patients, AppointmentRepository appointments, AdjustableClock clock,
                             ClinicStaffAccess staffAccess) {
        this.currentUser = currentUser;
        this.patients = patients;
        this.appointments = appointments;
        this.clock = clock;
        this.staffAccess = staffAccess;
    }

    public record Slot(Instant startsAt, String date, String time) {
    }

    public record BookRequest(String startsAt, String reason) {
    }

    public record AppointmentView(UUID id, Instant startsAt, String date, String time, String reason, Appointment.Status status) {
    }

    public record DayRow(UUID id, Instant startsAt, String time, UUID patientId, String fullName, String reason) {
    }

    @GetMapping("/api/clinic/slots")
    @Transactional(readOnly = true)
    public List<Slot> slots(@AuthenticationPrincipal Jwt jwt) {
        Clinic clinic = clinicOf(currentUser.from(jwt));
        return openSlots(clinic).stream().map(this::slot).toList();
    }

    @PostMapping("/api/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AppointmentView book(@RequestBody BookRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Patient patient = ownRecord(user);
        Instant startsAt;
        try {
            startsAt = Instant.parse(String.valueOf(request.startsAt()));
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt must be a time like 2026-09-23T01:00:00Z.");
        }
        Instant now = clock.instant();
        if (!ClinicHours.slots(now, zone()).contains(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is not an open slot. Pick one from the clinic's calendar.");
        }
        appointments.findFirstByPatientIdAndStatusAndStartsAtAfterOrderByStartsAt(patient.getId(), Appointment.Status.BOOKED, now)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "You already have a booking on " + slot(existing.getStartsAt()).date() + " at " + slot(existing.getStartsAt()).time()
                                    + ". Cancel it first to choose another time.");
                });
        if (appointments.existsBySlotKey(Appointment.slotKey(patient.getClinic().getId(), startsAt))) {
            throw taken();
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        if (reason != null && reason.length() > MAX_REASON) {
            reason = reason.substring(0, MAX_REASON);
        }
        try {
            Appointment booked = appointments.saveAndFlush(new Appointment(patient.getClinic(), patient, startsAt, reason, user.getId(), now));
            return view(booked);
        } catch (DataIntegrityViolationException e) {
            throw taken(); // someone booked the same slot a moment earlier
        }
    }

    @GetMapping("/api/appointments/mine")
    @Transactional(readOnly = true)
    public AppointmentView mine(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(currentUser.from(jwt));
        return appointments.findFirstByPatientIdAndStatusAndStartsAtAfterOrderByStartsAt(patient.getId(), Appointment.Status.BOOKED, clock.instant())
                .map(this::view)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No upcoming booking."));
    }

    @DeleteMapping("/api/appointments/{id}")
    @Transactional
    public AppointmentView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        Appointment appointment = appointments.findById(id)
                .filter(a -> a.getStatus() == Appointment.Status.BOOKED)
                .filter(a -> isThePatient(user, a) || worksAtTheClinic(user, a))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        appointment.cancel(clock.instant());
        return view(appointment);
    }

    @GetMapping("/api/clinic/appointments")
    @Transactional(readOnly = true)
    public List<DayRow> day(@RequestParam("date") LocalDate date, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = currentUser.from(jwt);
        if (!staffAccess.hasRole(user, ClinicStaffRole.DOCTOR) || user.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The day list is for clinic doctors.");
        }
        Instant from = date.atStartOfDay(zone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone()).toInstant();
        return appointments.findByClinicIdAndStatusAndStartsAtBetweenOrderByStartsAt(user.getClinic().getId(), Appointment.Status.BOOKED, from, to)
                .stream()
                .map(a -> new DayRow(a.getId(), a.getStartsAt(), slot(a.getStartsAt()).time(), a.getPatient().getId(),
                        a.getPatient().getFullName(), a.getReason()))
                .toList();
    }

    private List<Instant> openSlots(Clinic clinic) {
        Instant now = clock.instant();
        List<Instant> all = ClinicHours.slots(now, zone());
        if (all.isEmpty()) {
            return all;
        }
        Set<Instant> booked = appointments.findByClinicIdAndStatusAndStartsAtBetweenOrderByStartsAt(clinic.getId(), Appointment.Status.BOOKED,
                        all.get(0), all.get(all.size() - 1)).stream()
                .map(Appointment::getStartsAt)
                .collect(Collectors.toSet());
        return all.stream().filter(s -> !booked.contains(s)).toList();
    }

    private Clinic clinicOf(AppUser user) {
        return switch (user.getRole()) {
            case DOCTOR -> {
                if (user.getClinic() == null || !staffAccess.hasRole(user, ClinicStaffRole.DOCTOR)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                }
                yield user.getClinic();
            }
            case PATIENT -> ownRecord(user).getClinic();
            case NURSE, CLINIC_ADMIN -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clinic staff cannot book patient visits.");
            case CAREGIVER -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patients book their own visits.");
        };
    }

    private Patient ownRecord(AppUser user) {
        if (user.getRole() != Role.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patients book their own visits.");
        }
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }

    private static boolean isThePatient(AppUser user, Appointment a) {
        return a.getPatient().getAccount() != null && a.getPatient().getAccount().getId().equals(user.getId());
    }

    private boolean worksAtTheClinic(AppUser user, Appointment a) {
        return staffAccess.hasRole(user, ClinicStaffRole.DOCTOR) && user.getClinic() != null && user.getClinic().getId().equals(a.getClinic().getId());
    }

    private static ResponseStatusException taken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Someone has just booked that slot. Please pick another.");
    }

    private ZoneId zone() {
        return clock.getZone();
    }

    private Slot slot(Instant startsAt) {
        ZonedDateTime local = startsAt.atZone(zone());
        return new Slot(startsAt, local.toLocalDate().toString(), local.toLocalTime().toString());
    }

    private AppointmentView view(Appointment a) {
        Slot s = slot(a.getStartsAt());
        return new AppointmentView(a.getId(), a.getStartsAt(), s.date(), s.time(), a.getReason(), a.getStatus());
    }
}
