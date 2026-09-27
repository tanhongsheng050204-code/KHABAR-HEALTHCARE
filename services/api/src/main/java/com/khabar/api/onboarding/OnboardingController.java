package com.khabar.api.onboarding;

import com.khabar.api.config.AdjustableClock;
import com.khabar.api.graph.PatientGraphSync;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.CurrentUser;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turning a Supabase sign-in into a Khabar role. Clinics register patients and hand them a code;
 * patients invite caregivers; doctors invite colleagues; the very first clinic uses a bootstrap token.
 */
@RestController
public class OnboardingController {

    private static final Duration INVITE_LIFETIME = Duration.ofDays(7);

    private final CurrentUser currentUser;
    private final AppUserRepository users;
    private final ClinicRepository clinics;
    private final PatientRepository patients;
    private final CaregiverLinkRepository caregiverLinks;
    private final InviteRepository invites;
    private final AdjustableClock clock;
    private final String bootstrapToken;
    private final PatientGraphSync graphSync;
    private final com.khabar.api.clinicops.ClinicOps clinicOps;
    private final ClinicStaffAccess staffAccess;

    public OnboardingController(CurrentUser currentUser, AppUserRepository users, ClinicRepository clinics, PatientRepository patients,
                                CaregiverLinkRepository caregiverLinks, InviteRepository invites, AdjustableClock clock,
                                @Value("${khabar.onboarding.bootstrap-token:}") String bootstrapToken, PatientGraphSync graphSync,
                                ClinicStaffAccess staffAccess, com.khabar.api.clinicops.ClinicOps clinicOps) {
        this.currentUser = currentUser;
        this.users = users;
        this.clinics = clinics;
        this.patients = patients;
        this.caregiverLinks = caregiverLinks;
        this.invites = invites;
        this.clock = clock;
        this.bootstrapToken = bootstrapToken;
        this.graphSync = graphSync;
        this.clinicOps = clinicOps;
        this.staffAccess = staffAccess;
    }

    public record RegisterPatientRequest(String fullName, String icNumber, String phone, String preferredLanguage,
                                         List<String> allergies, Boolean pregnant) {
    }

    public record RegisteredPatient(UUID patientId, String inviteCode, Instant inviteExpiresAt) {
    }

    public record InviteCode(String inviteCode, Instant expiresAt) {
    }

    public record CaregiverInviteRequest(CaregiverScope scope) {
    }

    public record StaffInviteRequest(ClinicStaffRole role) {
    }

    public record AcceptRequest(String displayName) {
    }

    public record Accepted(Role role, String displayName) {
    }

    public record CaregiverView(UUID linkId, String caregiverName, CaregiverScope scope, Instant consentedAt) {
    }

    public record BootstrapRequest(String bootstrapToken, String clinicName, String displayName) {
    }

    @PostMapping("/api/clinic/patients")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RegisteredPatient registerPatient(@RequestBody RegisterPatientRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireRole(jwt, Role.DOCTOR);
        if (blank(request.fullName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The patient's full name is required.");
        }
        String icNumber = blank(request.icNumber()) ? null : request.icNumber().trim();
        if (icNumber != null && !icNumber.matches("\\d{12}|\\d{6}-\\d{2}-\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IC number must have 12 digits, with or without hyphens.");
        }
        String language = List.of("ms", "en", "zh", "ta").contains(request.preferredLanguage()) ? request.preferredLanguage() : "en";
        Patient patient = new Patient(doctor.getClinic(), null, request.fullName().trim(), icNumber, request.phone(), language);
        patient.recordAllergies(request.allergies());
        patient.setPregnant(Boolean.TRUE.equals(request.pregnant()));
        patients.save(patient);
        graphSync.changed(patient.getId());
        InviteCode code = issue(Invite.Kind.PATIENT_ACCOUNT, doctor.getClinic(), patient, null, doctor.getId());
        return new RegisteredPatient(patient.getId(), code.inviteCode(), code.expiresAt());
    }

    @PostMapping("/api/clinic/doctor-invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InviteCode inviteDoctor(@AuthenticationPrincipal Jwt jwt) {
        AppUser doctor = requireRole(jwt, Role.DOCTOR);
        return issue(Invite.Kind.DOCTOR, doctor.getClinic(), null, null, doctor.getId());
    }

    @PostMapping("/api/clinic/staff-invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InviteCode inviteStaff(@RequestBody StaffInviteRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser actor = currentUser.from(jwt);
        if (!staffAccess.canManageStaff(actor) || actor.getClinic() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic doctors or administrators can invite staff.");
        }
        if (request == null || request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a clinic staff role for this invitation.");
        }
        if (request.role() == ClinicStaffRole.DOCTOR && !staffAccess.hasRole(actor, ClinicStaffRole.DOCTOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a clinic doctor can invite another doctor.");
        }
        String code = InviteCodes.generate();
        Instant expires = clock.instant().plus(INVITE_LIFETIME);
        invites.save(new Invite(InviteCodes.hash(code), Invite.Kind.STAFF, actor.getClinic(), null, null,
                request.role(), actor.getId(), expires));
        clinicOps.record(actor, com.khabar.api.clinicops.ClinicActivity.Action.STAFF_INVITED,
                "Invitation for a " + request.role().name().toLowerCase().replace('_', ' '));
        return new InviteCode(code, expires);
    }

    @PostMapping("/api/patients/me/caregiver-invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InviteCode inviteCaregiver(@RequestBody CaregiverInviteRequest request, @AuthenticationPrincipal Jwt jwt) {
        AppUser user = requireRole(jwt, Role.PATIENT);
        Patient patient = ownRecord(user);
        CaregiverScope scope = request.scope() == null ? CaregiverScope.SUMMARY : request.scope();
        return issue(Invite.Kind.CAREGIVER, patient.getClinic(), patient, scope, user.getId());
    }

    @GetMapping("/api/patients/me/caregivers")
    @Transactional(readOnly = true)
    public List<CaregiverView> caregivers(@AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(requireRole(jwt, Role.PATIENT));
        return caregiverLinks.findByPatientIdAndRevokedAtIsNull(patient.getId()).stream()
                .map(l -> new CaregiverView(l.getId(), l.getCaregiver().getDisplayName(), l.getScope(), l.getConsentedAt()))
                .toList();
    }

    @DeleteMapping("/api/patients/me/caregivers/{linkId}")
    @Transactional
    public CaregiverView revokeCaregiver(@PathVariable UUID linkId, @AuthenticationPrincipal Jwt jwt) {
        Patient patient = ownRecord(requireRole(jwt, Role.PATIENT));
        CaregiverLink link = caregiverLinks.findById(linkId)
                .filter(l -> l.getPatient().getId().equals(patient.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        link.revoke(clock.instant());
        return new CaregiverView(link.getId(), link.getCaregiver().getDisplayName(), link.getScope(), link.getConsentedAt());
    }

    @PostMapping("/api/invites/{code}/accept")
    @Transactional
    public Accepted accept(@PathVariable String code, @RequestBody(required = false) AcceptRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUser.id(jwt);
        Invite invite = invites.findByCodeHash(InviteCodes.hash(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That code is not valid."));
        Instant now = clock.instant();
        if (!invite.usable(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "That code has already been used or has expired. Ask for a new one.");
        }
        Optional<AppUser> existing = users.findById(userId);
        String name = request == null || blank(request.displayName()) ? null : request.displayName().trim();

        AppUser user = switch (invite.getKind()) {
            case PATIENT_ACCOUNT -> {
                if (existing.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This sign-in is already registered.");
                }
                AppUser created = users.save(new AppUser(userId, Role.PATIENT, name != null ? name : firstName(invite.getPatient()), null));
                try {
                    invite.getPatient().linkAccount(created);
                } catch (IllegalStateException e) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
                }
                yield created;
            }
            case CAREGIVER -> {
                AppUser caregiver = existing.orElseGet(() -> users.save(new AppUser(userId, Role.CAREGIVER, name != null ? name : "Caregiver", null)));
                if (caregiver.getRole() != Role.CAREGIVER) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This sign-in is registered as a " + caregiver.getRole().name().toLowerCase() + ", not a caregiver.");
                }
                caregiverLinks.save(new CaregiverLink(invite.getPatient(), caregiver, invite.getScope()));
                yield caregiver;
            }
            case DOCTOR -> {
                if (existing.isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This sign-in is already registered.");
                }
                AppUser doctor = users.save(new AppUser(userId, Role.DOCTOR, name != null ? name : "Doctor", invite.getClinic()));
                staffAccess.grant(doctor, invite.getClinic(), ClinicStaffRole.DOCTOR, invite.getCreatedBy(), now);
                yield doctor;
            }
            case STAFF -> {
                ClinicStaffRole staffRole = invite.getStaffRole();
                if (staffRole == null || invite.getClinic() == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "That staff invitation is incomplete. Ask the clinic for a new one.");
                }
                AppUser staff;
                if (existing.isPresent()) {
                    staff = existing.get();
                    if (!isClinicStaff(staff.getRole()) || staff.getClinic() == null
                            || !staff.getClinic().getId().equals(invite.getClinic().getId())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This sign-in is already registered for a different Khabar role or clinic.");
                    }
                } else {
                    Role role = roleFor(staffRole);
                    String defaultName = staffRole == ClinicStaffRole.CLINIC_ADMIN ? "Clinic administrator"
                            : staffRole == ClinicStaffRole.NURSE ? "Nurse" : "Doctor";
                    staff = users.save(new AppUser(userId, role, name != null ? name : defaultName, invite.getClinic()));
                }
                staffAccess.grant(staff, invite.getClinic(), staffRole, invite.getCreatedBy(), now);
                yield staff;
            }
        };
        invite.markUsed(userId, now);
        return new Accepted(user.getRole(), user.getDisplayName());
    }

    @PostMapping("/api/onboarding/clinic")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Accepted createFirstClinic(@RequestBody BootstrapRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (bootstrapToken.isBlank() || request.bootstrapToken() == null
                || !MessageDigest.isEqual(bootstrapToken.getBytes(StandardCharsets.UTF_8), request.bootstrapToken().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong bootstrap token.");
        }
        UUID userId = currentUser.id(jwt);
        if (users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This sign-in is already registered.");
        }
        if (blank(request.clinicName()) || blank(request.displayName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clinic name and your name are required.");
        }
        Clinic clinic = clinics.save(new Clinic(request.clinicName().trim()));
        AppUser doctor = users.save(new AppUser(userId, Role.DOCTOR, request.displayName().trim(), clinic));
        staffAccess.grant(doctor, clinic, ClinicStaffRole.DOCTOR, userId, clock.instant());
        return new Accepted(doctor.getRole(), doctor.getDisplayName());
    }

    private InviteCode issue(Invite.Kind kind, Clinic clinic, Patient patient, CaregiverScope scope, UUID createdBy) {
        String code = InviteCodes.generate();
        Instant expires = clock.instant().plus(INVITE_LIFETIME);
        invites.save(new Invite(InviteCodes.hash(code), kind, clinic, patient, scope, createdBy, expires));
        return new InviteCode(code, expires);
    }

    private AppUser requireRole(Jwt jwt, Role role) {
        AppUser user = currentUser.from(jwt);
        boolean allowed = role == Role.DOCTOR
                ? staffAccess.hasRole(user, ClinicStaffRole.DOCTOR)
                : user.getRole() == role;
        if (!allowed || (role == Role.DOCTOR && user.getClinic() == null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private Patient ownRecord(AppUser user) {
        return patients.findByAccountId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No patient record is linked to this account."));
    }

    private static String firstName(Patient patient) {
        return patient.getFullName().split("\\s+")[0];
    }

    private static Role roleFor(ClinicStaffRole role) {
        return switch (role) {
            case DOCTOR -> Role.DOCTOR;
            case NURSE -> Role.NURSE;
            case CLINIC_ADMIN -> Role.CLINIC_ADMIN;
        };
    }

    private static boolean isClinicStaff(Role role) {
        return role == Role.DOCTOR || role == Role.NURSE || role == Role.CLINIC_ADMIN;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
