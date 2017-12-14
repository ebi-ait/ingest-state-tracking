package org.humancellatlas.ingest.messaging;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Component
@RequiredArgsConstructor
@Getter
public class MessageReceiver {
    private final @NonNull IngestApiClient ingestApiClient;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;


    @RabbitListener(queues = Constants.Queues.ENVELOPE_CREATED)
    public void receiveSubmissionEnvelopeCreatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference seRef = ingestApiClient.retrieveSubmissionEnvelopeReference(submissionEnvelopeMessage.getDocumentId());
        getSubmissionStateMonitor().monitorSubmissionEnvelope(seRef);
    }

    @RabbitListener(queues = Constants.Queues.ENVELOPE_UPDATE)
    public void receiveSubmissionEnvelopeUpdatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {

    }

    @RabbitListener(queues = Constants.Queues.DOCUMENT_CREATED)
    public void receiveMetadataDocumentCreatedMessage(MetadataDocumentMessage metadataDocumentMessage) {

    }

    @RabbitListener(queues = Constants.Queues.DOCUMENT_UPDATE)
    public void receiveMetadataDocumentupdatedMessage(MetadataDocumentMessage metadataDocumentMessage) {

    }
}
