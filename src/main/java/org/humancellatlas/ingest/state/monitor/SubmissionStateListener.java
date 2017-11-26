package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvents;
import org.humancellatlas.ingest.state.SubmissionStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public class SubmissionStateListener extends StateMachineListenerAdapter<SubmissionStates, SubmissionEvents> {
    private final SubmissionEnvelopeReference submissionEnvelopeReference;
    private final SubmissionStateMonitor submissionStateMonitor;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public SubmissionStateListener(SubmissionEnvelopeReference submissionEnvelopeReference, SubmissionStateMonitor submissionStateMonitor) {
        this.submissionEnvelopeReference = submissionEnvelopeReference;
        this.submissionStateMonitor = submissionStateMonitor;
    }

    @Override
    public void stateEntered(State<SubmissionStates, SubmissionEvents> state) {
        System.out.println("For realz, I would be sending a callback event to ingest-core here...");
        System.out.println(String.format("\tEnvelope '%s' -> State %s", submissionEnvelopeReference.getUuid(), state.getId().toString()));

    }

    @Override
    public void eventNotAccepted(Message<SubmissionEvents> eventMsg) {
        log.error(String.format("Submission event was not accepted: [%s : %s]", eventMsg.getHeaders().toString(), eventMsg.getPayload().toString()));
    }

    @Override
    public void stateMachineStopped(StateMachine<SubmissionStates, SubmissionEvents> stateMachine) {
        // TODO - check this event is triggered when the state machine enters it's exit state (ALL_EVENTS_COMPLETE)
        System.out.println("State machine stopped!");
        submissionStateMonitor.stopMonitoring(stateMachine);
    }
}
