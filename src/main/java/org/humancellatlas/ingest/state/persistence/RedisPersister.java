package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.data.StateMachineRepository;
import org.springframework.statemachine.data.redis.RedisRepositoryStateMachine;
import org.springframework.statemachine.data.redis.RedisRepositoryStateMachinePersist;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.statemachine.persist.RepositoryStateMachinePersist;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

/**
 * Created by rolando on 09/05/2018.
 */
@Service
@AllArgsConstructor
public class RedisPersister implements Persister {

    private final @NonNull StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory;
    private final @NonNull StateMachineRepository<RedisRepositoryStateMachine> stateMachineRepository;

    private final @NonNull RedisStateMachinePersister<SubmissionState, SubmissionEvent> persister;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public void persistStateMachines(Collection<StateMachine<SubmissionState, SubmissionEvent>> machines) {
        machines.forEach(machine -> {
            try {
                RedisRepositoryStateMachine r = new RedisRepositoryStateMachine();
                r.setMachineId(machine.getId());
                r.setId(machine.getId());
                this.stateMachineRepository.save(r);
                this.persister.persist(machine, machine.getId());
            } catch (Exception e) {
                log.error("Failed to persist state machine with id: " + machine.getId(), e);
            }
        });
    }

    public Collection<StateMachine<SubmissionState, SubmissionEvent>> retrieveStateMachines() {
        Collection<StateMachine<SubmissionState, SubmissionEvent>> machines = new HashSet<>();

        stateMachineRepository.findAll().forEach(savedStateMachine -> {
            String savedMachineId = savedStateMachine.getMachineId();
            StateMachine<SubmissionState, SubmissionEvent> stateMachine = stateMachineFactory.getStateMachine(savedMachineId);
            try {
                machines.add(persister.restore(stateMachine, savedMachineId));
            } catch (Exception e) {
                log.error("Failed to retrieve state machine with id: " + savedMachineId, e);
            }
        });

        return machines;
    }

    public Collection<String> deleteStateMachines(Collection<String> stateMachineIds) {
        Collection<String> deletedStateMachines = new HashSet<>();

        stateMachineIds.forEach(machineId -> {
            stateMachineRepository.delete(stateMachineRepository.findById(machineId).get());
            deletedStateMachines.add(machineId);
        });

        return deletedStateMachines;
    }

    public Collection<String> deleteAllStateMachines() {
        Collection<String> allStateMachinesIds = new HashSet<>();

        stateMachineRepository.findAll().forEach(redisRepositoryStateMachine -> {
            allStateMachinesIds.add(redisRepositoryStateMachine.getMachineId());
        });

        return deleteStateMachines(allStateMachinesIds);
    }
}
