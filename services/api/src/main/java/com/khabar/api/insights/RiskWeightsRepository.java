package com.khabar.api.insights;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiskWeightsRepository extends JpaRepository<RiskWeights, UUID> {
}
