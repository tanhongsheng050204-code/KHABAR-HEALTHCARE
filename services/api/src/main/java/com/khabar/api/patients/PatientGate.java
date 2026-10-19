package com.khabar.api.patients;

import com.khabar.api.audit.AuditAction;
import com.khabar.api.audit.AuditLog;
import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import com.khabar.api.identity.ClinicStaffRole;
import com.khabar.api.identity.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** The two checks every patient-data endpoint needs: who may read it (with the read logged), and who may change it. */
@Component
public class PatientGate {

    private final PatientRepository patients;
    private final PatientAccessPolicy policy;
    private final ClinicStaffAccess staffAccess;
    private final AuditLog auditLog;

    public PatientGate(PatientRepository patients, PatientAccessPolicy policy, ClinicStaffAccess staffAccess, AuditLog auditLog) {
        this.patients = patients;
        this.policy = policy;
        this.staffAccess = staffAccess;
        this.auditLog = auditLog;
    }

    /** The patient, their clinic's doctors, and caregivers the patient shares alerts with. Anyone else's read is refused; staff reads are logged. */
    public Patient read(AppUser user, UUID patientId) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.canView(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return patient;
    }

    /** Clinical detail (lab results): only the patient and their clinic's doctors, never caregivers. Staff reads are logged. */
    public Patient clinical(AppUser user, UUID patientId) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!policy.isPatientOrTheirClinic(user, patient)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (user.getRole() != Role.PATIENT) {
            auditLog.record(user, patient.getId(), AuditAction.VIEWED_RECORD);
        }
        return patient;
    }

    /** A doctor at the patient's own clinic. */
    public Patient doctorAt(AppUser user, UUID patientId) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!staffAccess.hasRole(user, ClinicStaffRole.DOCTOR) || user.getClinic() == null
                || !user.getClinic().getId().equals(patient.getClinic().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a doctor at the patient's clinic can change this.");
        }
        return patient;
    }
}
