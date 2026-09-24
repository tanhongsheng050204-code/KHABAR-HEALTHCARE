package com.khabar.api.patients;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.ClinicStaffAccess;
import org.springframework.stereotype.Component;

/**
 * Who may see a patient's record:
 * a doctor at the patient's clinic, the patient themself, or a caregiver the patient consented to.
 */
@Component
public class PatientAccessPolicy {

    private final CaregiverLinkRepository caregiverLinks;
    private final ClinicStaffAccess staffAccess;

    public PatientAccessPolicy(CaregiverLinkRepository caregiverLinks, ClinicStaffAccess staffAccess) {
        this.caregiverLinks = caregiverLinks;
        this.staffAccess = staffAccess;
    }

    public boolean canView(AppUser user, Patient patient) {
        if (staffAccess.hasClinicalAccess(user) && worksAtPatientsClinic(user, patient)) {
            return true;
        }
        return switch (user.getRole()) {
            case DOCTOR, NURSE, CLINIC_ADMIN -> false;
            case PATIENT -> isThePatient(user, patient);
            case CAREGIVER -> caregiverLinks.existsByPatientIdAndCaregiverIdAndRevokedAtIsNull(patient.getId(), user.getId());
        };
    }

    /** The access log is for the patient and their clinic; caregivers do not see it. */
    public boolean canReadAccessLog(AppUser user, Patient patient) {
        return isPatientOrTheirClinic(user, patient);
    }

    /** Clinical detail (the access log, intake answers) is for the patient and their clinic, not caregivers. */
    public boolean isPatientOrTheirClinic(AppUser user, Patient patient) {
        if (staffAccess.hasClinicalAccess(user) && worksAtPatientsClinic(user, patient)) {
            return true;
        }
        return switch (user.getRole()) {
            case DOCTOR, NURSE, CLINIC_ADMIN -> false;
            case PATIENT -> isThePatient(user, patient);
            case CAREGIVER -> false;
        };
    }

    private boolean worksAtPatientsClinic(AppUser user, Patient patient) {
        return user.getClinic() != null && user.getClinic().getId().equals(patient.getClinic().getId());
    }

    private boolean isThePatient(AppUser user, Patient patient) {
        return patient.getAccount() != null && patient.getAccount().getId().equals(user.getId());
    }
}
