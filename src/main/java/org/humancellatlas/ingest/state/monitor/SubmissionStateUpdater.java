package org.humancellatlas.ingest.state.monitor;

import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by rolando on 14/02/2018.
 */
@Service
public class SubmissionStateUpdater {
    @Getter
    private final IngestApiClient ingestApiClient;

    public SubmissionStateUpdater(@Autowired IngestApiClient ingestApiClient) {
        this.ingestApiClient = ingestApiClient;
    }

    public void update(SubmissionEnvelopeReference envelopeReference, SubmissionState submissionState) {
        getIngestApiClient().updateEnvelopeState(envelopeReference, submissionState);
    }

}
