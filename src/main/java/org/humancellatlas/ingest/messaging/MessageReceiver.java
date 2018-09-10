package org.humancellatlas.ingest.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.messaging.model.BundleCompletedMessage;
import org.humancellatlas.ingest.messaging.model.BundleSubmittedMessage;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.messaging.service.MessageHandler;
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
    public void receiveBundleableProcessSubmittedMessage(BundleSubmittedMessage bundleSubmittedMessage) {
      getMessageHandler().handleBundleableProcessSubmittedMessage(bundleSubmittedMessage);
    }

    @RabbitListener(queues = Constants.Queues.BUNDLEABLE_PROCESS_COMPLETED)
    public void receiveBundleableProcessCompletedMessage(BundleCompletedMessage bundleCompletedMessage) {
        getMessageHandler().handleBundleableProcessCompletedMessage(bundleCompletedMessage);
    }
}
