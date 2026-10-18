package com.khabar.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The end of the audit chain: one row. Sealing locks it, so two API instances can never give two entries the
 * same position.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    static final int ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private long lastSeq;

    @Column(nullable = false, length = 64)
    private String lastHash;

    private Instant sealedAt;

    protected AuditChainHead() {
    }

    AuditChainHead(String genesisHash) {
        this.id = ID;
        this.lastSeq = 0;
        this.lastHash = genesisHash;
    }

    void advance(long seq, String hash, Instant at) {
        this.lastSeq = seq;
        this.lastHash = hash;
        this.sealedAt = at;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public String getLastHash() {
        return lastHash;
    }

    public Instant getSealedAt() {
        return sealedAt;
    }
}
