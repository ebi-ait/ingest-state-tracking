package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

/**
 * Created by rolando on 15/05/2018.
 *
 * Finds all persisted state machines and loads them into the submission state monitor
 *
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

    private final Logger log = LoggerFactory.getLogger(getClass());

    public void autoLoad() {
        persister.retrieveStateMachines().forEach(stateMachine -> {
            UUID envelopeUuid = UUID.fromString(stateMachine.getId());
            try {
                SubmissionEnvelopeReference envelopeReference = ingestApiClient.referenceForSubmissionEnvelope(envelopeUuid);
                submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference, stateMachine);
            } catch (HttpClientErrorException e) {
                log.warn(String.format("HTTP error: Failed to retrieve envelope reference for envelope with UUID: %s", envelopeUuid.toString()), e);
            } catch (RuntimeException e) {
                log.error(String.format("Failed to autoload statemachine for envelope with UUID: %s", envelopeUuid.toString()), e);
            }
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        autoLoad();
    }
}
