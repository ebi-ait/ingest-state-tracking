package org.humancellatlas.ingest.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by rolando on 05/07/2018.
 */
@Service
@DependsOn("configuration")
@AllArgsConstructor
@Getter
public class MessageHandler {
    private final @NonNull ConfigurationService configurationService;
    private final @NonNull IngestApiClient ingestApiClient;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;

    private int numMetadataDocumentUpdateThreads = configurationService.getMetadataStateHandlerThreads();
    private ExecutorService metadataDocumentUpdatePool = Executors.newFixedThreadPool(numMetadataDocumentUpdateThreads);

    private final Logger log = LoggerFactory.getLogger(getClass());

    public void handleMetadataDocumentUpdate(MetadataDocumentMessage metadataDocumentMessage) {
        metadataDocumentUpdatePool.submit(() -> handleMetadataDocumentUpdate(metadataDocumentMessage));
    }

    public void handleSubmissionEnvelopeCreated(SubmissionEnvelopeMessage submissionEnvelopeMessage) {

    }

    private void doHandleMetadataDocumentUpdate(MetadataDocumentMessage metadataDocumentMessage) {
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
                    SubmissionState envelopeState = SubmissionState.valueOf(ingestApiClient.retrieveSubmissionEnvelope(envelopeReference)
                                                                                           .getSubmissionState()
                                                                                           .toUpperCase());
                    if(!envelopeState.after(SubmissionState.SUBMITTED)){
                        if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
                            submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
                        }
                        submissionStateMonitor.notifyOfMetadataDocumentState(documentReference, envelopeReference, documentState);
                    }
                });
    }

}
