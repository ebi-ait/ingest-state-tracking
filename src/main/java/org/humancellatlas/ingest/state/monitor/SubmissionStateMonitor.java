package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentInfo;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Component
public class SubmissionStateMonitor {
    private final StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory;

    // in memory map of currently running state machines
    private final Map<UUID, StateMachine<SubmissionState, SubmissionEvent>> stateMachineMap;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    public SubmissionStateMonitor(StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
        this.stateMachineMap = new HashMap<>();
    }

    public void monitorSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference) {
        monitorSubmissionEnvelope(submissionEnvelopeReference, true);
    }

    public void monitorSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference, boolean autoremove) {
        StateMachine<SubmissionState, SubmissionEvent> stateMachine =
                stateMachineFactory.getStateMachine(submissionEnvelopeReference.getUuid());
        stateMachine.addStateListener(new SubmissionStateListener(submissionEnvelopeReference, this, autoremove));

        stateMachine.start();
        stateMachineMap.put(submissionEnvelopeReference.getUuid(), stateMachine);
    }

    public void stopMonitoring(SubmissionEnvelopeReference submissionEnvelopeReference) {
        UUID submissionEnvelopeUuid = submissionEnvelopeReference.getUuid();
        if (stateMachineMap.containsKey(submissionEnvelopeUuid)) {
            removeStateMachine(submissionEnvelopeUuid);
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope '%s' is not currently being monitored", submissionEnvelopeUuid.toString()));
        }
    }

    public void stopMonitoring(StateMachine<SubmissionState, SubmissionEvent> stateMachine) {
        stateMachineMap.entrySet().removeIf(entry -> entry.getValue().equals(stateMachine));
    }

    public boolean isMonitoring(SubmissionEnvelopeReference submissionEnvelopeReference) {
        return stateMachineMap.containsKey(submissionEnvelopeReference.getUuid());
    }

    public SubmissionState findCurrentState(SubmissionEnvelopeReference submissionEnvelopeReference) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            return stateMachine.get().getState().getId();
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    submissionEnvelopeReference.getUuid()));
        }
    }

    public boolean sendEventForSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference,
                                                  SubmissionEvent event) {
        if (event.equals(SubmissionEvent.CONTENT_ADDED)) {
            throw new UnsupportedOperationException(
                    "CONTENT_ADDED events should be accompanied with an indication of the content that was added. " +
                            "Use 'notifyOfNewMetadataDocument()' instead.");
        }
        if (event.equals(SubmissionEvent.VALIDATION_STARTED)) {
            throw new UnsupportedOperationException(
                    "VALIDATION_STARTED events should be internally triggered only. " +
                            "Use 'notifyOfValidatedMetadataDocument()' instead.");
        }

        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();
            return machine.sendEvent(event);
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    submissionEnvelopeReference.getUuid()));
        }
    }

    public boolean notifyOfNewMetadataDocument(MetadataDocumentReference metadataDocumentReference,
                                               SubmissionEnvelopeReference submissionEnvelopeReference) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();

            Message<SubmissionEvent> message = MessageBuilder.withPayload(SubmissionEvent.CONTENT_ADDED)
                    .setHeader(MetadataDocumentInfo.DOCUMENT_ID, metadataDocumentReference.getUuid().toString())
                    .setHeader(MetadataDocumentInfo.DOCUMENT_STATE, MetadataDocumentState.DRAFT)
                    .build();

            return machine.sendEvent(message);
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    submissionEnvelopeReference.getUuid()));
        }
    }

    public boolean notifyOfValidatingMetadataDocument(MetadataDocumentReference metadataDocumentReference,
                                                      SubmissionEnvelopeReference submissionEnvelopeReference) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();

            if (machine.getExtendedState().getVariables().containsKey(metadataDocumentReference.getUuid().toString())) {
                // notify that validation has started
                return machine.sendEvent(SubmissionEvent.VALIDATION_STARTED);
            }
            else {
                throw new IllegalArgumentException(String.format(
                        "Submission envelope reference '%s' is not currently being monitored",
                        submissionEnvelopeReference.getUuid()));
            }
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    submissionEnvelopeReference.getUuid()));
        }
    }

    public boolean notifyOfValidatedMetadataDocument(MetadataDocumentReference metadataDocumentReference,
                                                     SubmissionEnvelopeReference submissionEnvelopeReference,
                                                     boolean isValid) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();
            MessageBuilder<SubmissionEvent> messageBuilder =
                    MessageBuilder.withPayload(SubmissionEvent.DOCUMENT_PROCESSED)
                            .setHeader(MetadataDocumentInfo.DOCUMENT_ID,
                                       metadataDocumentReference.getUuid().toString());

            if (isValid) {
                messageBuilder.setHeader(MetadataDocumentInfo.DOCUMENT_STATE, MetadataDocumentState.VALID);
            }
            else {
                messageBuilder.setHeader(MetadataDocumentInfo.DOCUMENT_STATE, MetadataDocumentState.INVALID);
            }
            return machine.sendEvent(messageBuilder.build());
        }
        else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    submissionEnvelopeReference.getUuid()));
        }
    }

    private void removeStateMachine(UUID submissionEnvelopeUuid) {
        stateMachineMap.remove(submissionEnvelopeUuid);
    }

    private Optional<StateMachine<SubmissionState, SubmissionEvent>> findStateMachine(UUID submissionEnvelopeUuid) {
        if (stateMachineMap.containsKey(submissionEnvelopeUuid)) {
            return Optional.of(stateMachineMap.get(submissionEnvelopeUuid));
        }
        else {
            return Optional.empty();
        }
    }
}
