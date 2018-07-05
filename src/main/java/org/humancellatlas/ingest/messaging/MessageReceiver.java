package org.humancellatlas.ingest.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.messaging.model.BundleCompletedMessage;
import org.humancellatlas.ingest.messaging.model.BundleSubmittedMessage;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.messaging.service.MessageHandler;
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
import org.springframework.context.annotation.DependsOn;
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
@DependsOn("configuration")
@AllArgsConstructor
public class MessageReceiver {
    private final @NonNull ConfigurationService configuration;
    private final @NonNull IngestApiClient ingestApiClient;
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;
    private final  @NonNull MessageHandler messageHandler;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @RabbitListener(queues = Constants.Queues.ENVELOPE_CREATED)
    public void receiveSubmissionEnvelopeCreatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        getMessageHandler().handleSubmissionEnvelopeCreated(submissionEnvelopeMessage);
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

    @RabbitListener(queues = Constants.Queues.DOCUMENT_UPDATE)
    public void receiveMetadataDocumentUpdatedMessage(MetadataDocumentMessage metadataDocumentMessage) {
        getMessageHandler().handleMetadataDocumentUpdate(metadataDocumentMessage);
    }

    @RabbitListener(queues = Constants.Queues.BUNDLEABLE_PROCESS_SUBMITTED)
    public void receiveBundleableProcessSubmittedMessage(BundleSubmittedMessage bundleSubmittedMessage) {
        /* track the newly submitted bundleable process */
        submissionStateMonitor.notifyOfBundleState(bundleSubmittedMessage.getDocumentId(),
                                                   bundleSubmittedMessage.getEnvelopeUuid(),
                                                   bundleSubmittedMessage.getTotal(),
                                                   MetadataDocumentState.PROCESSING);
    }

    @RabbitListener(queues = Constants.Queues.BUNDLEABLE_PROCESS_COMPLETED)
    public void receiveBundleableProcessCompletedMessage(BundleCompletedMessage bundleCompletedMessage) {
        submissionStateMonitor.notifyOfBundleState(bundleCompletedMessage.getDocumentId(),
                                                   bundleCompletedMessage.getEnvelopeUuid(),
                                                   bundleCompletedMessage.getTotalBundles(),
                                                   MetadataDocumentState.COMPLETE);
    }

}
