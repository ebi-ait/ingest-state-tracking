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

    private void doHandleMetadataDocumentUpdate(MetadataDocumentMessage metadataDocumentMessage) {
        MetadataDocumentReference documentReference = getIngestApiClient().referenceForMetadataDocument(metadataDocumentMessage);
        MetadataDocument metadataDocument = new MetadataDocument();
        try{
            metadataDocument.setReferencedEnvelope(this.getIngestApiClient().envelopeReferencesFromEnvelopeId(metadataDocumentMessage.getEnvelopeId()));
            metadataDocument.setValidationState(metadataDocumentMessage.getValidationState());
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
        SubmissionEnvelopeReference envelopeReference = metadataDocument.getReferencedEnvelope();

        SubmissionState envelopeState = SubmissionState.valueOf(ingestApiClient.retrieveSubmissionEnvelope(envelopeReference)
                                                                               .getSubmissionState()
                                                                               .toUpperCase());
        if(!envelopeState.after(SubmissionState.SUBMITTED)){
            if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
                submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
            }
            submissionStateMonitor.notifyOfMetadataDocumentState(documentReference, envelopeReference, documentState);
        }
    }

    private void doHandleSubmissionEnvelopeCreated(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference seRef = ingestApiClient.referenceForSubmissionEnvelope(submissionEnvelopeMessage);
        getSubmissionStateMonitor().monitorSubmissionEnvelope(seRef);
    }

    private void doHandleSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        SubmissionEnvelopeReference envelopeReference = getIngestApiClient().referenceForSubmissionEnvelope(submissionEnvelopeMessage);

        if(!submissionStateMonitor.isMonitoring(envelopeReference)) {
            submissionStateMonitor.monitorSubmissionEnvelope(envelopeReference);
        }

        SubmissionEvent submissionEvent = SubmissionEvent.fromRequestedSubmissionState(SubmissionState.valueOf(submissionEnvelopeMessage.getRequestedState().toUpperCase()));
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
