package org.humancellatlas.ingest.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.messaging.model.DocumentCompletedMessage;
import org.humancellatlas.ingest.messaging.model.DocumentProcessingMessage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by rolando on 05/07/2018.
 */
@Service
@DependsOn("configuration")
@Getter
public class MessageHandler {
    private final @NonNull ConfigurationService configurationService;
    private final @NonNull IngestApiClient ingestApiClient;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;

    private final int numHandlerThreads;
    private final Workers workers;

    public MessageHandler(@Autowired ConfigurationService configurationService,
                          @Autowired IngestApiClient ingestApiClient,
                          @Autowired SubmissionStateMonitor submissionStateMonitor) {
        this.configurationService = configurationService;
        this.ingestApiClient = ingestApiClient;
        this.submissionStateMonitor = submissionStateMonitor;

        this.numHandlerThreads = configurationService.getNumHandlerThreads();
        this.workers = new Workers(this.numHandlerThreads);
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    public void handleMetadataDocumentUpdate(MetadataDocumentMessage metadataDocumentMessage) {
        workers.submit(() -> doHandleMetadataDocumentUpdate(metadataDocumentMessage), metadataDocumentMessage.getDocumentId());
    }

    public void handleMetadataDocumentDelete(String metadataDocumentId, String envelopeId) {
        workers.submit(() -> doHandleMetadataDocumentDelete(metadataDocumentId, envelopeId), metadataDocumentId);
    }

    public void handleSubmissionEnvelopeCreated(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        workers.submit(() -> doHandleSubmissionEnvelopeCreated(submissionEnvelopeMessage), submissionEnvelopeMessage.getDocumentId());
    }

    public void handleSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        workers.submit(() -> doHandleSubmissionEnvelopeStateUpdateRequest(submissionEnvelopeMessage), submissionEnvelopeMessage.getDocumentId());
    }

    public void handleDocumentProcessingMessageForSubmissionEvent(DocumentProcessingMessage documentProcessingMessage, SubmissionEvent submissionEvent) {
        workers.submit(() -> doHandleDocumentProcessingMessage(documentProcessingMessage, submissionEvent), documentProcessingMessage.getDocumentId());

    }

    public void handleDocumentCompletedMessageForSubmissionEvent(DocumentCompletedMessage documentCompletedMessage, SubmissionEvent submissionEvent) {
        workers.submit(() -> doHandleDocumentCompletedMessage(documentCompletedMessage, submissionEvent), documentCompletedMessage.getDocumentId());
    }

    private MetadataDocument getMetadataDocument(String metadataDocumentId, String envelopeId) {
        MetadataDocument metadataDocument = new MetadataDocument();
        try {
            metadataDocument.setReferencedEnvelope(this.getIngestApiClient().envelopeReferencesFromEnvelopeId(envelopeId));
        } catch (HttpClientErrorException e) {
            log.error(String.format("Failed to fetch metadata document. Response was: %s Message was: ", e.getResponseBodyAsString()));
            try {
                log.error(new ObjectMapper().writeValueAsString(metadataDocumentId));
            } catch (IOException ioe) {
                throw new AmqpRejectAndDontRequeueException(e);
            }
            throw new AmqpRejectAndDontRequeueException(e);
        }

        return metadataDocument;
    }

    private Boolean canNotify(SubmissionEnvelopeReference envelopeReference) {
        SubmissionState envelopeState = SubmissionState.fromString(this.getIngestApiClient().retrieveSubmissionEnvelope(envelopeReference)
                .getSubmissionState());

        return !envelopeState.after(SubmissionState.EXPORTED);
    }

    private void doHandleMetadataDocumentUpdate(MetadataDocumentMessage metadataDocumentMessage) {
        log.info("updating {} document with id {} and uuid {} from submission {} to state {}",
                metadataDocumentMessage.getDocumentType(),
                metadataDocumentMessage.getDocumentId(),
                metadataDocumentMessage.getDocumentUuid(),
                metadataDocumentMessage.getEnvelopeId(),
                metadataDocumentMessage.getValidationState());
        MetadataDocumentReference documentReference = getIngestApiClient().referenceForMetadataDocument(metadataDocumentMessage);
        MetadataDocument metadataDocument = getMetadataDocument(metadataDocumentMessage.getDocumentId(),
                metadataDocumentMessage.getEnvelopeId());
        metadataDocument.setValidationState(metadataDocumentMessage.getValidationState());

        MetadataDocumentState documentState = MetadataDocumentState.valueOf(metadataDocument.getValidationState().toUpperCase());
        SubmissionEnvelopeReference envelopeReference = metadataDocument.getReferencedEnvelope();

        if(canNotify(envelopeReference)){
            if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
                submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
            }

            submissionStateMonitor.notifyOfMetadataDocumentState(documentReference, envelopeReference, documentState);
        }
    }

    private void doHandleMetadataDocumentDelete(String metadataDocumentId, String envelopeId) {
        log.info("deleting document with id {} in submission {}",
                metadataDocumentId,
                envelopeId);
        SubmissionEnvelopeReference envelopeReference = getMetadataDocument(metadataDocumentId, envelopeId)
                .getReferencedEnvelope();

        if(canNotify(envelopeReference)){
            if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
                throw new RuntimeException(String.format("Cannot delete from envelope %s since it is not being monitored", envelopeId));
            }
            submissionStateMonitor.notifyOfMetadataDocumentDelete(metadataDocumentId, envelopeReference);
        }
    }

    private void doHandleSubmissionEnvelopeCreated(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference seRef = ingestApiClient.referenceForSubmissionEnvelope(submissionEnvelopeMessage);
        getSubmissionStateMonitor().monitorSubmissionEnvelope(seRef);
    }

    private void doHandleSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        log.info("updating submission with id {} and uuid {} to state {}",
                submissionEnvelopeMessage.getDocumentId(),
                submissionEnvelopeMessage.getDocumentUuid(),
                submissionEnvelopeMessage.getRequestedState());

        SubmissionEnvelopeReference envelopeReference = getIngestApiClient().referenceForSubmissionEnvelope(submissionEnvelopeMessage);

        if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
            submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
        }

        SubmissionEvent submissionEvent = SubmissionEvent.fromRequestedSubmissionState(SubmissionState.fromString(submissionEnvelopeMessage.getRequestedState()));
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeReference, submissionEvent);
    }

    private void doHandleDocumentProcessingMessage(DocumentProcessingMessage processingMessage, SubmissionEvent submissionEvent) {
        submissionStateMonitor.notifyOfDocumentState(processingMessage.getDocumentId(),
                                                   processingMessage.getEnvelopeUuid(),
                                                   processingMessage.getTotal(),
                                                   MetadataDocumentState.PROCESSING,
                                                   submissionEvent);
    }

    private void doHandleDocumentCompletedMessage(DocumentCompletedMessage completedMessage, SubmissionEvent submissionEvent) {

        submissionStateMonitor.notifyOfDocumentState(completedMessage.getDocumentId(),
                                                   completedMessage.getEnvelopeUuid(),
                                                   completedMessage.getTotal(),
                                                   MetadataDocumentState.COMPLETE,
                                                   submissionEvent);
    }

    private class Workers {
        final int numWorkerThreads;
        final List<ExecutorService> workers;

        Workers(int numWorkerThreads){
            this.numWorkerThreads = numWorkerThreads;
            this.workers = IntStream.range(0,numWorkerThreads)
                                    .mapToObj(x -> Executors.newSingleThreadExecutor())
                                    .collect(Collectors.toList());
        }

        void submit(Runnable runnable, String resourceId) {
            int workerIndex = this.workerIndexForResourceId(resourceId, this.numWorkerThreads);
            ExecutorService worker = this.workers.get(workerIndex);
            worker.submit(runnable);
        }

        int workerIndexForResourceId(String resourceId, int numWorkers) {
            BigInteger resourceValue = new BigInteger(resourceId, 16); // assuming resource is hex
            return resourceValue.mod(BigInteger.valueOf(numWorkers)).intValue();
        }
    }

}
