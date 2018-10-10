package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoPersister.class);
    private static final int AUTO_PERSIST_INTERVAL_EVERY_MINUTE = 1000 * 60;

    private final @NonNull Persister persister;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;

    @Scheduled(fixedDelay = AUTO_PERSIST_INTERVAL_EVERY_MINUTE)
    public void autoPersist() {
        LOGGER.debug("Begin persisting state machines...");
        persister.persistStateMachines(submissionStateMonitor.getStateMachines());
    }

}
