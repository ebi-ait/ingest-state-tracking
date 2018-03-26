package org.humancellatlas.ingest.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.messaging.model.AssayCompletedMessage;
import org.humancellatlas.ingest.messaging.model.AssaySubmittedMessage;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;

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

    private final Logger log = LoggerFactory.getLogger(getClass());

    @RabbitListener(queues = Constants.Queues.ENVELOPE_CREATED)
    public void receiveSubmissionEnvelopeCreatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference seRef = ingestApiClient.referenceForSubmissionEnvelope(submissionEnvelopeMessage);
        getSubmissionStateMonitor().monitorSubmissionEnvelope(seRef);
    }

    @RabbitListener(queues = Constants.Queues.ENVELOPE_UPDATE)
    public void receiveSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference envelopeReference = getIngestApiClient().referenceForSubmissionEnvelope(submissionEnvelopeMessage);

        if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
            submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
        }

        SubmissionEvent submissionEvent = SubmissionEvent.fromRequestedSubmissionState(SubmissionState.valueOf(submissionEnvelopeMessage.getRequestedState().toUpperCase()));
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeReference, submissionEvent);
    }

    @RabbitListener(queues = Constants.Queues.DOCUMENT_UPDATE, concurrency = "10")
    public void receiveMetadataDocumentupdatedMessage(MetadataDocumentMessage metadataDocumentMessage) {
        MetadataDocumentReference documentReference = getIngestApiClient().referenceForMetadataDocument(metadataDocumentMessage);
        MetadataDocument metadataDocument;
        try{
            metadataDocument = getIngestApiClient().retrieveMetadataDocument(documentReference, metadataDocumentMessage.getEnvelopeIds());
        } catch (HttpClientErrorException e) {
            log.info(String.format("Failed to fetch metadata document. Response was: %s Message was: ", e.getResponseBodyAsString()));
            try {
                log.info(new ObjectMapper().writeValueAsString(metadataDocumentMessage));
            } catch (IOException ioe) {
                throw new AmqpRejectAndDontRequeueException(e);
            }
            throw new AmqpRejectAndDontRequeueException(e);
        }

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

    @RabbitListener(queues = Constants.Queues.ASSAY_SUBMITTED)
    public void receiveAssaySubmittedMessage(AssaySubmittedMessage assaySubmittedMessage) {
        /* track the newly submitted assay */
        submissionStateMonitor.notifyOfAssayState(assaySubmittedMessage.getDocumentId(),
                                                  assaySubmittedMessage.getEnvelopeUuid(),
                                                  assaySubmittedMessage.getTotalAssays(),
                                                  MetadataDocumentState.PROCESSING);
    }

    @RabbitListener(queues = Constants.Queues.ASSAY_COMPLETED)
    public void receiveAssayCompletedMessage(AssayCompletedMessage assayCompletedMessage) {
        /* track the newly submitted assay */
        submissionStateMonitor.notifyOfAssayState(assayCompletedMessage.getDocumentId(),
                                                  assayCompletedMessage.getEnvelopeUuid(),
                                                  assayCompletedMessage.getTotalAssays(),
                                                  MetadataDocumentState.COMPLETE);
    }

}
