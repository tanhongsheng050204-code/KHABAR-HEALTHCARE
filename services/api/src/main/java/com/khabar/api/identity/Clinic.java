package com.khabar.api.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "clinic")
public class Clinic {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected Clinic() {
    }

    public Clinic(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
