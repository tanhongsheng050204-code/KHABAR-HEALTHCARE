package com.khabar.api.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PhotoDraftRepository extends JpaRepository<PhotoDraft, UUID> {
}
