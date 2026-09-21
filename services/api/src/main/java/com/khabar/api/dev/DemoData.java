package com.khabar.api.dev;

import com.khabar.api.identity.AppUser;
import com.khabar.api.identity.AppUserRepository;
import com.khabar.api.identity.Clinic;
import com.khabar.api.identity.ClinicRepository;
import com.khabar.api.identity.Role;
import com.khabar.api.patients.CaregiverLink;
import com.khabar.api.patients.CaregiverLinkRepository;
import com.khabar.api.patients.CaregiverScope;
import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Fake people for the `local` profile. Every name, IC and phone number here is made up. */
@Component
@Profile("local")
public class DemoData implements ApplicationRunner {

    public static final UUID DOCTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final UUID AMINAH_ACCOUNT_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    public static final UUID NURUL_ID = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private final ClinicRepository clinics;
    private final AppUserRepository users;
    private final PatientRepository patients;
    private final CaregiverLinkRepository caregiverLinks;

    public DemoData(ClinicRepository clinics, AppUserRepository users, PatientRepository patients, CaregiverLinkRepository caregiverLinks) {
        this.clinics = clinics;
        this.users = users;
        this.patients = patients;
        this.caregiverLinks = caregiverLinks;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsById(DOCTOR_ID)) {
            return;
        }
        Clinic clinic = clinics.save(new Clinic("Klinik Dr Priya (demo)"));
        users.save(new AppUser(DOCTOR_ID, Role.DOCTOR, "Dr Priya", clinic));
        AppUser aminahAccount = users.save(new AppUser(AMINAH_ACCOUNT_ID, Role.PATIENT, "Aminah", null));
        AppUser nurul = users.save(new AppUser(NURUL_ID, Role.CAREGIVER, "Nurul", null));

        Patient aminah = patients.save(new Patient(clinic, aminahAccount, "Aminah binti Yusof", "590312-10-5566", "012-345 6789", "ms"));
        patients.save(new Patient(clinic, null, "Tan Kok Hoe", "540101-07-1234", "016-222 3333", "zh"));
        patients.save(new Patient(clinic, null, "Muthu a/l Rajan", "610815-08-4321", "019-444 5555", "ta"));
        caregiverLinks.save(new CaregiverLink(aminah, nurul, CaregiverScope.SUMMARY_AND_ALERTS));
    }
}
