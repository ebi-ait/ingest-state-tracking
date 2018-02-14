package org.humancellatlas.ingest.messaging;

import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

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
        SubmissionEnvelopeReference seRef = ingestApiClient.referenceForSubmissionEnvelope(submissionEnvelopeMessage.getDocumentId());
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
        MetadataDocumentReference documentReference = getIngestApiClient().referenceForMetadataDocument(metadataDocumentMessage);
        MetadataDocument metadataDocument = getIngestApiClient().retrieveMetadataDocument(documentReference);

        MetadataDocumentState documentState = MetadataDocumentState.valueOf(metadataDocument.getValidationState().toUpperCase());
        Collection<SubmissionEnvelopeReference> relatedEnvelopeReferences =
                metadataDocument
                        .getSubmissionIds()
                        .stream()
                        .map(envelopeId -> getIngestApiClient().referenceForSubmissionEnvelope(envelopeId))
                        .collect(Collectors.toList());

        relatedEnvelopeReferences.forEach(envelopeReference -> submissionStateMonitor.notifyOfMetadataDocumentState(documentReference, envelopeReference, documentState));
    }
}
