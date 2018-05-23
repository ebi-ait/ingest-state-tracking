package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Created by rolando on 15/05/2018.
 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(
        value = "app.auto-persist.enable", havingValue = "true", matchIfMissing = true
)
public class AutoPersister {
    private final @NonNull Persister persister;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;
    private static final int AUTO_PERSIST_INTERVAL_EVERY_MINUTE = 1000 * 60;

    @Scheduled(fixedDelay = AUTO_PERSIST_INTERVAL_EVERY_MINUTE)
    public void autoPersist() {
        persister.persistStateMachines(submissionStateMonitor.getStateMachines());
    }
}
