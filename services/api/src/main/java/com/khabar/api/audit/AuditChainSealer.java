package com.khabar.api.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Seals new record-access entries into the chain every minute, so the unsealed gap stays short. */
@Component
@ConditionalOnProperty(name = "khabar.audit.sealer-enabled", havingValue = "true", matchIfMissing = true)
public class AuditChainSealer {

    private final AuditChain chain;

    public AuditChainSealer(AuditChain chain) {
        this.chain = chain;
    }

    @Scheduled(fixedDelayString = "${khabar.audit.seal-ms:60000}", initialDelayString = "${khabar.audit.seal-ms:60000}")
    public void seal() {
        chain.sealAll();
    }
}
