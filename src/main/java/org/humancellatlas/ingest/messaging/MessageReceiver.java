package org.humancellatlas.ingest.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.messaging.model.DocumentCompletedMessage;
import org.humancellatlas.ingest.messaging.model.DocumentProcessingMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.messaging.service.MessageHandler;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Component
@Getter
@AllArgsConstructor
public class MessageReceiver {
    private final @NonNull MessageHandler messageHandler;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @RabbitListener(queues = Constants.Queues.ENVELOPE_CREATED)
    public void receiveSubmissionEnvelopeCreatedMessage(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
        getMessageHandler().handleSubmissionEnvelopeCreated(submissionEnvelopeMessage);
    }

    @RabbitListener(queues = Constants.Queues.ENVELOPE_UPDATE)
    public void receiveSubmissionEnvelopeStateUpdateRequest(SubmissionEnvelopeMessage submissionEnvelopeMessage) {
       getMessageHandler().handleSubmissionEnvelopeStateUpdateRequest(submissionEnvelopeMessage);
    }

    @RabbitListener(queues = Constants.Queues.BUNDLEABLE_PROCESS_SUBMITTED)
    public void receiveManifestProcessingMessage(DocumentProcessingMessage documentProcessingMessage) {
      getMessageHandler().handleDocumentProcessingMessageForSubmissionEvent(documentProcessingMessage, SubmissionEvent.PROCESSING_STATE_UPDATE);
    }

    @RabbitListener(queues = Constants.Queues.BUNDLEABLE_PROCESS_COMPLETED)
    public void receiveManifestCompletedMessage(DocumentCompletedMessage documentCompletedMessage) {
        getMessageHandler().handleDocumentCompletedMessageForSubmissionEvent(documentCompletedMessage, SubmissionEvent.PROCESSING_STATE_UPDATE);
    }

    @RabbitListener(queues = Constants.Queues.EXPERIMENT_SUBMITTED)
    public void receiveExperimentProcessingMessage(DocumentProcessingMessage documentProcessingMessage) {
        getMessageHandler().handleDocumentProcessingMessageForSubmissionEvent(documentProcessingMessage, SubmissionEvent.EXPORTING_STATE_UPDATE);
    }

    @RabbitListener(queues = Constants.Queues.EXPERIMENT_COMPLETED)
    public void receiveExperimentCompletedMessage(DocumentCompletedMessage documentCompletedMessage) {
        getMessageHandler().handleDocumentCompletedMessageForSubmissionEvent(documentCompletedMessage, SubmissionEvent.EXPORTING_STATE_UPDATE);
    }
}
