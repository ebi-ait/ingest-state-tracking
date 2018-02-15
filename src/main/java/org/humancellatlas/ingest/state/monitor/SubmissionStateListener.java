package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
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
public class SubmissionStateListener extends StateMachineListenerAdapter<SubmissionState, SubmissionEvent> {
    private final SubmissionEnvelopeReference submissionEnvelopeReference;
    private final SubmissionStateMonitor submissionStateMonitor;
    private final boolean autoremove;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public SubmissionStateListener(SubmissionEnvelopeReference submissionEnvelopeReference,
                                   SubmissionStateMonitor submissionStateMonitor,
                                   boolean autoremove) {
        this.submissionEnvelopeReference = submissionEnvelopeReference;
        this.submissionStateMonitor = submissionStateMonitor;
        this.autoremove = autoremove;
        log.info(String.format("\tCreated Envelope '%s'", submissionEnvelopeReference.getUuid()));
    }

    @Override
    public void stateEntered(State<SubmissionState, SubmissionEvent> state) {
        log.info(String.format("\tEnvelope '%s' -> State %s",
                               submissionEnvelopeReference.getUuid(),
                               state.getId().toString()));

    }

    @Override
    public void eventNotAccepted(Message<SubmissionEvent> eventMsg) {
        log.error(String.format("Submission event was not accepted: [%s : %s]",
                                eventMsg.getHeaders().toString(),
                                eventMsg.getPayload().toString()));
    }

    @Override
    public void stateMachineStopped(StateMachine<SubmissionState, SubmissionEvent> stateMachine) {
        // TODO - check this event is triggered when the state machine enters it's exit state (ALL_EVENTS_COMPLETE)
        log.info(String.format("State machine '%s' stopped!", submissionEnvelopeReference.getUuid()));
        if (autoremove) {
            submissionStateMonitor.stopMonitoring(stateMachine);
        }
    }

    @Override
    public void stateChanged(State<SubmissionState, SubmissionEvent> from, State<SubmissionState, SubmissionEvent> to) {

    }
}
