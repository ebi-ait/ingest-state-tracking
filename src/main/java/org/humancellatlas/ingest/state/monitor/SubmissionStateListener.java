package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.exception.CoreStateUpdatedFailedException;
import org.humancellatlas.ingest.messaging.Constants;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.util.AssayBundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.HashMap;
import java.util.Map;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public class SubmissionStateListener extends StateMachineListenerAdapter<SubmissionState, SubmissionEvent> {
    private final SubmissionEnvelopeReference submissionEnvelopeReference;
    private final SubmissionStateMonitor submissionStateMonitor;
    private final SubmissionStateUpdater submissionStateUpdater;
    private final boolean autoremove;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected SubmissionStateListener(SubmissionEnvelopeReference submissionEnvelopeReference,
                            SubmissionStateMonitor submissionStateMonitor,
                            SubmissionStateUpdater submissionStateUpdater,
                            boolean autoremove) {
        this.submissionEnvelopeReference = submissionEnvelopeReference;
        this.submissionStateMonitor = submissionStateMonitor;
        this.submissionStateUpdater = submissionStateUpdater;
        this.autoremove = autoremove;
        log.info(String.format("\tCreated Envelope '%s'", submissionEnvelopeReference.getUuid()));
    }


    @Override
    public void stateMachineStarted(StateMachine<SubmissionState, SubmissionEvent> stateMachine) {
        // add the metadata document state map
        Map<String, MetadataDocumentState> metadataDocumentTracker = new HashMap<>();
        stateMachine.getExtendedState().getVariables().put(Constants.METADATA_DOCUMENT_TRACKER, metadataDocumentTracker);
    }

    @Override
    public void stateEntered(State<SubmissionState, SubmissionEvent> state) {

    }

    @Override
    public void eventNotAccepted(Message<SubmissionEvent> eventMsg) {
        log.error(String.format("Submission event was not accepted(Current state: %s): [%s : %s]",
                                submissionStateMonitor.findCurrentState(submissionEnvelopeReference).toString(),
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
        if(! from.getId().equals(to.getId())) {
            log.info(String.format("\tEnvelope '%s' -> State %s",
                                   submissionEnvelopeReference.getUuid(),
                                   to.getId().toString()));

            this.submissionStateUpdater.requestStateUpdateForEnvelope(submissionEnvelopeReference, to.getId());
        }
    }
}
