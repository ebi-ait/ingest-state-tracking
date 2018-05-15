package org.humancellatlas.ingest.state.persistence;

import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rolando on 15/05/2018.
 *
 * Use in unit tests or if disabled Redis persistence is desired
 */
@Service
public class InMemoryPersister implements Persister {
    private final Map<String, StateMachine<SubmissionState, SubmissionEvent>> inMemoryMachines = new ConcurrentHashMap<>();

    @Override
    public void persistStateMachines(Collection<StateMachine<SubmissionState, SubmissionEvent>> machines) {
        machines.forEach(machine -> inMemoryMachines.put(machine.getId(), machine));
    }

    @Override
    public Collection<StateMachine<SubmissionState, SubmissionEvent>> retrieveStateMachines() {
        return inMemoryMachines.values();
    }

    @Override
    public Collection<String> deleteStateMachines(Collection<String> stateMachineIds) {
        stateMachineIds.forEach(inMemoryMachines::remove);
        return stateMachineIds;
    }

    @Override
    public Collection<String> deleteAllStateMachines() {
        Collection<String> machineIds = this.inMemoryMachines.keySet();
        this.inMemoryMachines.clear();
        return machineIds;
    }
}
