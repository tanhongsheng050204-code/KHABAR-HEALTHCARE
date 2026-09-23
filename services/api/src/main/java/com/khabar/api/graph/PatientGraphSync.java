package com.khabar.api.graph;

import com.khabar.api.patients.Patient;
import com.khabar.api.patients.PatientRepository;
import com.khabar.api.patients.Redactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the patient graph in step with Postgres. Code that changes what the graph shows about a patient
 * calls {@link #changed}; once that transaction commits, the patient's facts are rebuilt and written.
 * Postgres is the record: if the graph is down, the change still stands and the next one catches up.
 */
@Component
public class PatientGraphSync {

    private static final Logger log = LoggerFactory.getLogger(PatientGraphSync.class);

    private final PatientGraph graph;
    private final GraphFactsBuilder builder;
    private final PatientRepository patients;
    private final TransactionTemplate readInNewTransaction;

    public PatientGraphSync(PatientGraph graph, GraphFactsBuilder builder, PatientRepository patients,
                            PlatformTransactionManager transactions) {
        this.graph = graph;
        this.builder = builder;
        this.patients = patients;
        this.readInNewTransaction = new TransactionTemplate(transactions);
        // After a commit the old transaction's resources are still bound, so reading needs a fresh one.
        this.readInNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readInNewTransaction.setReadOnly(true);
    }

    /** The patient's graph facts may have changed: write them once the current transaction commits. */
    public void changed(UUID patientId) {
        if (!graph.enabled()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sync(patientId);
            return;
        }
        @SuppressWarnings("unchecked")
        Set<UUID> pending = (Set<UUID>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            Set<UUID> fresh = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(this, fresh);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List.copyOf(fresh).forEach(PatientGraphSync.this::sync);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PatientGraphSync.this);
                }
            });
            pending = fresh;
        }
        pending.add(patientId);
    }

    /** Writes one patient's facts now. Returns false (and logs why) if they could not be written. */
    public boolean sync(UUID patientId) {
        try {
            GraphFacts facts = readInNewTransaction.execute(status -> patients.findById(patientId).map(patient -> {
                GraphFacts built = builder.build(patient);
                requireNoIdentity(built, patient);
                return built;
            }).orElse(null));
            if (facts != null) {
                graph.write(facts);
            }
            return facts != null;
        } catch (RuntimeException e) {
            log.warn("Patient graph not updated for one patient: {}", e.getMessage());
            return false;
        }
    }

    /** Writes every patient's facts, for filling a new or emptied graph. Returns how many were written. */
    public int syncAll() {
        if (!graph.enabled()) {
            return 0;
        }
        List<UUID> ids = readInNewTransaction.execute(status -> patients.findAll().stream().map(Patient::getId).toList());
        return (int) ids.stream().filter(this::sync).count();
    }

    /**
     * The last lock before anything leaves for the graph: no text may still contain the patient's name,
     * IC or phone number. The builder redacts everything, so this only fails if a new field forgets to.
     */
    static void requireNoIdentity(GraphFacts facts, Patient patient) {
        for (String text : facts.texts()) {
            if (!Redactor.redact(text, patient).equals(text)) {
                throw new IllegalStateException("Refusing to write identifying text to the patient graph.");
            }
        }
    }
}
