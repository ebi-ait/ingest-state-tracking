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
    private final SubmissionStateListenerBuilder submissionStateListenerBuilder;

    // in memory map of currently running state machines
    private final Map<UUID, StateMachine<SubmissionState, SubmissionEvent>> stateMachineMap;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    public SubmissionStateMonitor(StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory, SubmissionStateListenerBuilder submissionStateListenerBuilder) {
        this.stateMachineFactory = stateMachineFactory;
        this.submissionStateListenerBuilder = submissionStateListenerBuilder;
        this.stateMachineMap = new HashMap<>();
    }

    public void monitorSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference) {
        monitorSubmissionEnvelope(submissionEnvelopeReference, true);
    }

    public void monitorSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference, boolean autoremove) {
        StateMachine<SubmissionState, SubmissionEvent> stateMachine =
                stateMachineFactory.getStateMachine(submissionEnvelopeReference.getUuid());
        stateMachine.addStateListener(submissionStateListenerBuilder.listenerFor(submissionEnvelopeReference, this, autoremove));

        stateMachine.start();
        stateMachineMap.put(UUID.fromString(submissionEnvelopeReference.getUuid()), stateMachine);
    }

    public void stopMonitoring(SubmissionEnvelopeReference submissionEnvelopeReference) {
        UUID submissionEnvelopeUuid = UUID.fromString(submissionEnvelopeReference.getUuid());
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
        return stateMachineMap.containsKey(UUID.fromString(submissionEnvelopeReference.getUuid()));
    }

    public SubmissionState findCurrentState(SubmissionEnvelopeReference submissionEnvelopeReference) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(UUID.fromString(submissionEnvelopeReference.getUuid()));
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
                findStateMachine(UUID.fromString(submissionEnvelopeReference.getUuid()));
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

    public boolean notifyOfMetadataDocumentState(MetadataDocumentReference metadataDocumentReference, SubmissionEnvelopeReference submissionEnvelopeReference, MetadataDocumentState state){
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
            findStateMachine(UUID.fromString(submissionEnvelopeReference.getUuid()));

        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();

            Message<SubmissionEvent> message = MessageBuilder.withPayload(SubmissionEvent.DOCUMENT_PROCESSED)
                .setHeader(MetadataDocumentInfo.DOCUMENT_ID, metadataDocumentReference.getId())
                .setHeader(MetadataDocumentInfo.DOCUMENT_STATE, state)
                .build();

            return machine.sendEvent(message);
        }
        else {
            throw new IllegalArgumentException(String.format(
                "Submission envelope reference '%s' is not currently being monitored",
                submissionEnvelopeReference.getUuid()));
        }
    }

    public boolean notifyOfBundleState(String bundleableProcessDocumentId, String envelopeUuid, int totalBundlesExpected, MetadataDocumentState bundleState) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine = findStateMachine(UUID.fromString(envelopeUuid));

        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();

            Message<SubmissionEvent> message = MessageBuilder.withPayload(SubmissionEvent.BUNDLE_STATE_UPDATE)
                                                             .setHeader(MetadataDocumentInfo.DOCUMENT_ID, bundleableProcessDocumentId)
                                                             .setHeader(MetadataDocumentInfo.DOCUMENT_STATE, bundleState)
                                                             .setHeader(MetadataDocumentInfo.BUNDLES_TOTAL_EXPECTED, totalBundlesExpected)
                                                             .setHeader(MetadataDocumentInfo.ENVELOPE_UUID, envelopeUuid)
                                                             .build();

            return machine.sendEvent(message);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope reference '%s' is not currently being monitored",
                    envelopeUuid));
        }
    }

    private void removeStateMachine(UUID submissionEnvelopeUuid) {
        stateMachineMap.remove(submissionEnvelopeUuid);
    }

    public Optional<StateMachine<SubmissionState, SubmissionEvent>> findStateMachine(UUID submissionEnvelopeUuid) {
        if (stateMachineMap.containsKey(submissionEnvelopeUuid)) {
            return Optional.of(stateMachineMap.get(submissionEnvelopeUuid));
        }
        else {
            return Optional.empty();
        }
    }
}
