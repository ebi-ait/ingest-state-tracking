package org.humancellatlas.ingest.state.monitor;

import lombok.AccessLevel;
import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.exception.CoreStateUpdatedFailedException;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.model.PendingSubmissionUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Created by rolando on 14/02/2018.
 */
@Service
public class SubmissionStateUpdater {
    @Getter(AccessLevel.PRIVATE) private final IngestApiClient ingestApiClient;
    @Getter(AccessLevel.PRIVATE) private final ConfigurationService config;
    private final Map<String, PendingSubmissionUpdate> pendingUpdates;
    private final ScheduledExecutorService executorService;

    private final Logger log = LoggerFactory.getLogger(getClass());


    public SubmissionStateUpdater(@Autowired IngestApiClient ingestApiClient, @Autowired ConfigurationService config) {
        this.ingestApiClient = ingestApiClient;
        this.config = config;
        this.pendingUpdates = new ConcurrentHashMap<>();
        this.executorService = new ScheduledThreadPoolExecutor(1);

        this.init();
    }

    public void requestStateUpdateForEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference, SubmissionState submissionState) {
        this.pendingUpdates.put(submissionEnvelopeReference.getId(), new PendingSubmissionUpdate(submissionEnvelopeReference, submissionState));
    }

    private void update(SubmissionEnvelopeReference envelopeReference, SubmissionState submissionState) throws CoreStateUpdatedFailedException {
        log.info(String.format("Updating state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()));
        try {
            SubmissionEnvelope updatedEnvelope = getIngestApiClient().updateEnvelopeState(envelopeReference, submissionState);
            if(! updatedEnvelope.getSubmissionState().toUpperCase().equals(submissionState.toString().toUpperCase())) {
                throw new CoreStateUpdatedFailedException(String.format("Failed to updated state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()));
            }
        } catch (HttpClientErrorException e) {
            if(e.getRawStatusCode() == HttpStatus.NOT_FOUND.value()) {
                log.info(String.format("Tried to update the state of a non-existent envelope with ID %s", envelopeReference.getId()));
                // remove it from envelopes to be updated
                this.pendingUpdates.remove(envelopeReference.getId());
            } else {
                throw new CoreStateUpdatedFailedException(String.format("Failed to updated state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()), e);
            }
        } catch (RuntimeException e) {
            throw new CoreStateUpdatedFailedException(String.format("Failed to updated state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()), e);
        }
    }

    private void update(PendingSubmissionUpdate pendingSubmissionUpdate) throws CoreStateUpdatedFailedException {
        update(pendingSubmissionUpdate.getEnvelopeReference(), pendingSubmissionUpdate.getToState());
    }

    public void persistStates() {
        Set<String> updatedEnvelopeIds = new HashSet<>();

        for(Map.Entry<String, PendingSubmissionUpdate> entry : pendingUpdates.entrySet()) {
            String envelopeId = entry.getKey();
            PendingSubmissionUpdate pendingSubmissionUpdate = entry.getValue();
            try {
                update(pendingSubmissionUpdate);
                updatedEnvelopeIds.add(envelopeId);
            } catch (CoreStateUpdatedFailedException e) {
                log.trace("Failed to update state in the core", e);
            }
        }

        updatedEnvelopeIds.forEach(envelopeId -> this.pendingUpdates.remove(envelopeId));
    }

    private void init() {
        this.executorService.scheduleAtFixedRate(() -> {
            try {
                persistStates();
            } catch (Throwable e) {
                log.error("Error/Exception occurred trying to persist states", e);
            }
        }, 5, (long) this.getConfig().getUpdaterPeriodSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        this.executorService.shutdown();
    }

    public Collection<PendingSubmissionUpdate> getPendingUpdates() {
        return this.pendingUpdates.values();
    }

}
