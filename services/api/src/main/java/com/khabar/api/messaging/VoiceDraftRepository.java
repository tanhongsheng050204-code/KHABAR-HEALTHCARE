package com.khabar.api.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoiceDraftRepository extends JpaRepository<VoiceDraft, UUID> {
}
