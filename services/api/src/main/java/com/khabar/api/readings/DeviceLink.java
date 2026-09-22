package com.khabar.api.readings;

import com.khabar.api.patients.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A home device (its Favoriot developer id) that sends readings for one patient. */
@Entity
@Table(name = "device_link")
public class DeviceLink {

    @Id
    @Column(length = 200)
    private String deviceId;

    @ManyToOne(optional = false)
    private Patient patient;

    @Column(nullable = false)
    private UUID linkedBy;

    @Column(nullable = false)
    private Instant linkedAt;

    protected DeviceLink() {
    }

    public DeviceLink(String deviceId, Patient patient, UUID linkedBy, Instant linkedAt) {
        this.deviceId = deviceId;
        this.patient = patient;
        this.linkedBy = linkedBy;
        this.linkedAt = linkedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Patient getPatient() {
        return patient;
    }
}
