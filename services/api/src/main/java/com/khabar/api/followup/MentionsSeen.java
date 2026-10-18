package com.khabar.api.followup;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** When a staff member last opened their @mentions, so newer ones can be counted as unread. */
@Entity
@Table(name = "mentions_seen")
public class MentionsSeen {

    @Id
    private UUID userId;

    private Instant seenAt;

    protected MentionsSeen() {
    }

    public MentionsSeen(UUID userId, Instant seenAt) {
        this.userId = userId;
        this.seenAt = seenAt;
    }

    void seen(Instant at) {
        this.seenAt = at;
    }

    public Instant getSeenAt() {
        return seenAt;
    }
}
