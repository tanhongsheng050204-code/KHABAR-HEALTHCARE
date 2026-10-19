package com.khabar.api.insights;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A clinic's own points for each risk rule, as "RULE=points,RULE=points". Rules not listed use the default. */
@Entity
@Table(name = "risk_weights")
public class RiskWeights {

    @Id
    private UUID clinicId;

    @Column(nullable = false, length = 2000)
    private String weights;

    private UUID updatedBy;

    private Instant updatedAt;

    protected RiskWeights() {
    }

    public RiskWeights(UUID clinicId) {
        this.clinicId = clinicId;
        this.weights = "";
    }

    void update(String weights, UUID by, Instant at) {
        this.weights = weights;
        this.updatedBy = by;
        this.updatedAt = at;
    }

    public UUID getClinicId() { return clinicId; }
    public String getWeights() { return weights; }
    public Instant getUpdatedAt() { return updatedAt; }
}
