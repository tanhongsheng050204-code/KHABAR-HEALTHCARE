package com.khabar.api.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A person who can sign in. The id is the Supabase Auth user id (the JWT "sub"),
 * so a verified token maps straight to one row here.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String displayName;

    /** The clinic a doctor works at. Patients and caregivers reach clinics through patient records. */
    @ManyToOne
    private Clinic clinic;

    protected AppUser() {
    }

    public AppUser(UUID id, Role role, String displayName, Clinic clinic) {
        this.id = id;
        this.role = role;
        this.displayName = displayName;
        this.clinic = clinic;
    }

    public UUID getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Clinic getClinic() {
        return clinic;
    }
}
