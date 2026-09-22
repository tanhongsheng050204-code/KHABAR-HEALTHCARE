package com.khabar.api.patients;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

/** Keeps a patient's phone blind index in step with their (encrypted) phone number. */
@Component
public class PhoneIndexListener {

    private final PhoneIndex phoneIndex;

    public PhoneIndexListener(PhoneIndex phoneIndex) {
        this.phoneIndex = phoneIndex;
    }

    @PrePersist
    @PreUpdate
    void index(Patient patient) {
        patient.setPhoneIndex(phoneIndex.of(patient.getPhone()));
    }
}
