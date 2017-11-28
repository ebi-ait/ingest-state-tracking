package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvents;
import org.humancellatlas.ingest.state.SubmissionStates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Component
public class SubmissionStateMonitor {
    private final StateMachineFactory<SubmissionStates, SubmissionEvents> stateMachineFactory;

    // in memory map of currently running state machines
    private final Map<UUID, StateMachine<SubmissionStates, SubmissionEvents>> stateMachineMap;

    @Autowired
    public SubmissionStateMonitor(StateMachineFactory<SubmissionStates, SubmissionEvents> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
        this.stateMachineMap = new HashMap<>();
    }

    public void monitorSubmissionEnvelope(SubmissionEnvelopeReference submissionEnvelopeReference) {
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine =
                stateMachineFactory.getStateMachine(submissionEnvelopeReference.getUuid());
        stateMachine.addStateListener(new SubmissionStateListener(submissionEnvelopeReference, this));

        stateMachine.start();
        stateMachineMap.put(submissionEnvelopeReference.getUuid(), stateMachine);
    }

    public void stopMonitoring(SubmissionEnvelopeReference submissionEnvelopeReference) {
        UUID submissionEnvelopeUuid = submissionEnvelopeReference.getUuid();
        if (stateMachineMap.containsKey(submissionEnvelopeUuid)) {
            removeStateMachine(submissionEnvelopeUuid);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Submission envelope '%s' is not currently being monitored", submissionEnvelopeUuid.toString()));
        }
    }

    public void stopMonitoring(StateMachine<SubmissionStates, SubmissionEvents> stateMachine) {
        stateMachineMap.entrySet().removeIf(entry -> entry.getValue().equals(stateMachine));
    }

    private void removeStateMachine(UUID submissionEnvelopeUuid) {
        stateMachineMap.remove(submissionEnvelopeUuid);
    }

    public Optional<StateMachine<SubmissionStates, SubmissionEvents>> findStateMachine(UUID submissionEnvelopeUuid) {
        if (stateMachineMap.containsKey(submissionEnvelopeUuid)) {
            return Optional.of(stateMachineMap.get(submissionEnvelopeUuid));
        } else {
            return Optional.empty();
        }
    }

    public void sendEventForSubmissionEnvelope(UUID submissionEnvelopeUuid, SubmissionEvents event) {
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> stateMachine =
                findStateMachine(submissionEnvelopeUuid);
        if (stateMachine.isPresent()) {
            StateMachine<SubmissionStates, SubmissionEvents> machine = stateMachine.get();
            machine.sendEvent(event);
        }
    }
}
