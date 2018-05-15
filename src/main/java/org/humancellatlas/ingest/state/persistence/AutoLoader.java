package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Created by rolando on 15/05/2018.
 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(
        value = "app.auto-load.enable", havingValue = "true", matchIfMissing = true
)
public class AutoLoader implements InitializingBean {
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;
    private final @NonNull Persister persister;

    public void autoLoad() {
        submissionStateMonitor.loadStateMachines(persister.retrieveStateMachines());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        autoLoad();
    }
}
