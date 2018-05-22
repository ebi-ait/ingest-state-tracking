package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
    private final @NonNull IngestApiClient ingestApiClient;

    public void autoLoad() {
        persister.retrieveStateMachines().forEach(stateMachine -> {
            UUID envelopeUuid = UUID.fromString(stateMachine.getId());
            SubmissionEnvelopeReference envelopeReference = ingestApiClient.referenceForSubmissionEnvelope(envelopeUuid);
            submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference, stateMachine);
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        autoLoad();
    }
}
