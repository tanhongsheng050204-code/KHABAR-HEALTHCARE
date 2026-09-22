package com.khabar.api.patients;

import com.khabar.api.identity.AppUser;
import org.springframework.stereotype.Component;

/**
 * Who may see a patient's record:
 * a doctor at the patient's clinic, the patient themself, or a caregiver the patient consented to.
 */
@Component
public class PatientAccessPolicy {

    private final CaregiverLinkRepository caregiverLinks;

    public PatientAccessPolicy(CaregiverLinkRepository caregiverLinks) {
        this.caregiverLinks = caregiverLinks;
    }

    public boolean canView(AppUser user, Patient patient) {
        return switch (user.getRole()) {
            case DOCTOR -> worksAtPatientsClinic(user, patient);
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
        return switch (user.getRole()) {
            case DOCTOR -> worksAtPatientsClinic(user, patient);
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
