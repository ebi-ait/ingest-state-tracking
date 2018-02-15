package org.humancellatlas.ingest.state.monitor;

import lombok.AccessLevel;
import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.exception.CoreStateUpdatedFailedException;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by rolando on 14/02/2018.
 */
@Service
public class SubmissionStateUpdater {
    @Getter(AccessLevel.PRIVATE)
    private final IngestApiClient ingestApiClient;
    private final Logger log = LoggerFactory.getLogger(getClass());


    public SubmissionStateUpdater(@Autowired IngestApiClient ingestApiClient) {
        this.ingestApiClient = ingestApiClient;
    }

    public void update(SubmissionEnvelopeReference envelopeReference, SubmissionState submissionState) throws CoreStateUpdatedFailedException {
        log.info(String.format("Updating state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()));
        try {
            SubmissionEnvelope updatedEnvelope = getIngestApiClient().updateEnvelopeState(envelopeReference, submissionState);
            if(! updatedEnvelope.getSubmissionState().toUpperCase().equals(submissionState.toString().toUpperCase())) {
                throw new CoreStateUpdatedFailedException(String.format("Failed to updated state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()));
            }
        } catch (RuntimeException e) {
            throw new CoreStateUpdatedFailedException(String.format("Failed to updated state of envelope with ID %s to state %s", envelopeReference.getId(), submissionState.toString()));
        }

    }

}
