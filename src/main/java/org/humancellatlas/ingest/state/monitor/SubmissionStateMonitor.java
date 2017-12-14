package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.beans.factory.annotation.Autowired;
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

    public Optional<SubmissionState> findCurrentState(SubmissionEnvelopeReference submissionEnvelopeReference) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        return stateMachine.map(sm -> sm.getState().getId());
    }

    public Optional<Boolean> sendEventForSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference,
                                                            SubmissionEvent event) {
        Optional<StateMachine<SubmissionState, SubmissionEvent>> stateMachine =
                findStateMachine(submissionEnvelopeReference.getUuid());
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> machine = stateMachine.get();
            return Optional.of(machine.sendEvent(event));
        }
        else {
            return Optional.empty();
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
