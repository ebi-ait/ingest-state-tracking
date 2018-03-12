package org.humancellatlas.ingest.messaging;

import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Component
@Getter
public class MessageReceiver {
    private @Autowired IngestApiClient ingestApiClient;
    private @Autowired SubmissionStateMonitor submissionStateMonitor;


    @RabbitListener(queues = Constants.Queues.ENVELOPE_CREATED)
    public void receiveSubmissionEnvelopeCreatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference seRef = ingestApiClient.referenceForSubmissionEnvelope(submissionEnvelopeMessage);
        getSubmissionStateMonitor().monitorSubmissionEnvelope(seRef);
    }

    @RabbitListener(queues = Constants.Queues.ENVELOPE_UPDATE)
    public void receiveSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEvent submissionEvent = SubmissionEvent.valueOf(submissionEnvelopeMessage.getRequestedState());
        submissionStateMonitor.sendEventForSubmissionEnvelope(getIngestApiClient().referenceForSubmissionEnvelope(submissionEnvelopeMessage), submissionEvent);
    }

    @RabbitListener(queues = Constants.Queues.DOCUMENT_UPDATE)
    public void receiveMetadataDocumentupdatedMessage(MetadataDocumentMessage metadataDocumentMessage) {
        MetadataDocumentReference documentReference = getIngestApiClient().referenceForMetadataDocument(metadataDocumentMessage);
        MetadataDocument metadataDocument = getIngestApiClient().retrieveMetadataDocument(documentReference);

        MetadataDocumentState documentState = MetadataDocumentState.valueOf(metadataDocument.getValidationState().toUpperCase());
        metadataDocument
                .getReferencedEnvelopes()
                .forEach(envelopeReference -> {
                    if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
                        submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
                    }
                    submissionStateMonitor.notifyOfMetadataDocumentState(documentReference, envelopeReference, documentState);
                });
    }
}
