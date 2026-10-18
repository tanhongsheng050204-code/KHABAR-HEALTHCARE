package com.khabar.api.audit;

import com.khabar.api.config.AdjustableClock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Makes the record-access log tamper-evident. Each entry is sealed, in the order it was written, with
 * SHA-256(previous hash, its position and its own fields). Changing, deleting or reordering any sealed entry
 * breaks every hash after it, which {@link #verify()} reports. The newest hash is published (see
 * AuditChainController) and copied outside the database every day, so even rewriting the whole chain shows.
 *
 * Writing an access never waits for sealing: entries are sealed shortly after, by the sealer or before any
 * verify or anchor. An entry can be edited in the database during that short gap without detection.
 */
@Service
public class AuditChain {

    public static final String GENESIS = "0".repeat(64);
    private static final int BATCH = 500;
    private static final char SEP = '\u001f';

    private final AuditEntryRepository entries;
    private final AuditChainHeadRepository heads;
    private final AdjustableClock clock;
    private final TransactionTemplate tx;

    public AuditChain(AuditEntryRepository entries, AuditChainHeadRepository heads, AdjustableClock clock, TransactionTemplate tx) {
        this.entries = entries;
        this.heads = heads;
        this.clock = clock;
        this.tx = tx;
    }

    public record Verification(boolean intact, long checked, long unsealed, Long brokenAt, String problem,
                               long tailSeq, String tailHash, Instant checkedAt) {
    }

    public record Anchor(long seq, String hash, Instant sealedAt) {
    }

    /** Seals every unsealed entry. Returns how many were sealed. Safe to run on several instances at once. */
    public int sealAll() {
        int total = 0;
        while (true) {
            Integer sealed;
            try {
                sealed = tx.execute(status -> sealBatch());
            } catch (DataIntegrityViolationException raced) {
                // Another instance created the head row or sealed the same rows first; the next run carries on.
                return total;
            }
            if (sealed == null || sealed == 0) {
                return total;
            }
            total += sealed;
        }
    }

    private int sealBatch() {
        AuditChainHead head = heads.lockById(AuditChainHead.ID).orElse(null);
        if (head == null) {
            heads.saveAndFlush(new AuditChainHead(GENESIS));
            head = heads.lockById(AuditChainHead.ID).orElseThrow();
        }
        List<AuditEntry> pending = entries.findByHashIsNullOrderByIdAsc(PageRequest.of(0, BATCH));
        long seq = head.getLastSeq();
        String prev = head.getLastHash();
        for (AuditEntry entry : pending) {
            seq++;
            String hash = hash(seq, prev, entry);
            entry.seal(seq, prev, hash);
            prev = hash;
        }
        if (!pending.isEmpty()) {
            head.advance(seq, prev, clock.instant());
        }
        return pending.size();
    }

    /** Seals first, then recomputes every hash in order and checks each link. */
    public Verification verify() {
        sealAll();
        return tx.execute(status -> {
            status.setRollbackOnly();
            AuditChainHead head = heads.findById(AuditChainHead.ID).orElse(null);
            long tailSeq = head == null ? 0 : head.getLastSeq();
            String tailHash = head == null ? GENESIS : head.getLastHash();
            long unsealed = entries.countByHashIsNull();
            String prev = GENESIS;
            long expected = 1;
            while (expected <= tailSeq) {
                List<AuditEntry> page = entries.findByChainSeqGreaterThanEqualOrderByChainSeqAsc(expected, PageRequest.of(0, 1000));
                if (page.isEmpty()) {
                    return broken(expected, "Entry " + expected + " is missing.", expected - 1, unsealed, tailSeq, tailHash);
                }
                for (AuditEntry entry : page) {
                    if (entry.getChainSeq() != expected) {
                        return broken(expected, "Entry " + expected + " is missing.", expected - 1, unsealed, tailSeq, tailHash);
                    }
                    if (!prev.equals(entry.getPrevHash())) {
                        return broken(expected, "Entry " + expected + " does not point to the entry before it.", expected - 1, unsealed, tailSeq, tailHash);
                    }
                    String recomputed = hash(expected, prev, entry);
                    if (!recomputed.equals(entry.getHash())) {
                        return broken(expected, "Entry " + expected + " was changed after it was sealed.", expected - 1, unsealed, tailSeq, tailHash);
                    }
                    prev = recomputed;
                    expected++;
                    if (expected > tailSeq) {
                        break;
                    }
                }
            }
            if (!prev.equals(tailHash)) {
                return broken(tailSeq, "The end of the chain does not match its recorded hash.", tailSeq, unsealed, tailSeq, tailHash);
            }
            return new Verification(true, tailSeq, unsealed, null, null, tailSeq, tailHash, clock.instant());
        });
    }

    private Verification broken(long at, String problem, long checked, long unsealed, long tailSeq, String tailHash) {
        return new Verification(false, checked, unsealed, at, problem, tailSeq, tailHash, clock.instant());
    }

    /** The newest sealed position and hash: the value worth copying somewhere the database cannot reach. */
    public Anchor anchor() {
        sealAll();
        return heads.findById(AuditChainHead.ID)
                .map(h -> new Anchor(h.getLastSeq(), h.getLastHash(), h.getSealedAt()))
                .orElse(new Anchor(0, GENESIS, null));
    }

    /** The hash stored at one position, to compare with an anchor copied earlier. */
    @Transactional(readOnly = true)
    public String hashAt(long seq) {
        return entries.findByChainSeq(seq).map(AuditEntry::getHash).orElse(null);
    }

    static String hash(long seq, String prevHash, AuditEntry e) {
        String canonical = String.join(String.valueOf(SEP),
                Long.toString(seq),
                prevHash,
                e.getPatientId().toString(),
                e.getActorId().toString(),
                e.getActorLabel(),
                e.getAction().name(),
                // Milliseconds: what every supported database keeps exactly.
                Long.toString(e.getAt().toEpochMilli()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e1) {
            throw new IllegalStateException(e1);
        }
    }
}
