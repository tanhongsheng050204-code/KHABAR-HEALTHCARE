package com.khabar.api.followup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MentionsSeenRepository extends JpaRepository<MentionsSeen, UUID> {
}
