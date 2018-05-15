package org.humancellatlas.ingest.state.persistence;

import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.statemachine.StateMachine;

import java.util.Collection;

/**
 * Created by rolando on 15/05/2018.
 */
public interface Persister {
    void persistStateMachines(Collection<StateMachine<SubmissionState, SubmissionEvent>> machines);
    Collection<StateMachine<SubmissionState, SubmissionEvent>> retrieveStateMachines();
    Collection<String> deleteStateMachines(Collection<String> stateMachineIds);

}
