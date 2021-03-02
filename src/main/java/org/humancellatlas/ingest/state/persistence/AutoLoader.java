package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.exception.UnrecognisedSubmissionStateException;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.data.StateMachineRepository;
import org.springframework.statemachine.data.redis.RedisRepositoryStateMachine;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.humancellatlas.ingest.state.SubmissionState.*;


/**
 * Created by rolando on 15/05/2018.
 *
 * Finds all persisted state machines and loads them into the submission state monitor
 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(
        value = "app.auto-load.enable", havingValue = "true", matchIfMissing = true
)
public class AutoLoader implements InitializingBean {
    private final @NonNull SubmissionStateMonitor submissionStateMonitor;
    private final @NonNull Persister persister;
    private final @NonNull IngestApiClient ingestApiClient;
    private final @NonNull StateMachineRepository<RedisRepositoryStateMachine> stateMachineRepository;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public void loadStateMachines() {
        persister.retrieveStateMachines().forEach(stateMachine -> {

            try {
                UUID envelopeUuid = UUID.fromString(stateMachine.getId());

                Optional<SubmissionEnvelopeReference> envelope = getEnvelope(envelopeUuid);

                if (envelope.isEmpty()) {
                    deleteStateMachine(stateMachine);
                } else {
                    updateState(stateMachine, envelope.get());
                    submissionStateMonitor.monitorSubmissionEnvelope(envelope.get(), stateMachine);
                    String state = stateMachine.getState().getId().toString();
                    log.info(String.format("Restored %s with current state: %s", envelopeUuid.toString(), state));
                }

            } catch (RuntimeException e) {
                String desc = String.format("An unexpected error has occurred in loading the state machine for submission %s: %s",
                        stateMachine.getId(), e.getMessage());

                String action = "Please check the stack trace.";

                handleException(desc, action, e);
            }
        });
    }

    private Optional<SubmissionEnvelopeReference> getEnvelope(UUID envelopeUuid) {
        try {
            SubmissionEnvelopeReference envelope = ingestApiClient.referenceForSubmissionEnvelope(envelopeUuid);
            return Optional.of(envelope);
        } catch (UnrecognisedSubmissionStateException e) {
            String desc = String.format("A submission state value is not recognised in the state tracker: %s", e.getMessage());
            String action = "Consider defining a new SubmissionState enum value";
            handleException(desc, action, e);
        } catch (HttpClientErrorException.NotFound e) {
            log.info(String.format("Submission %s was not found.", envelopeUuid.toString()));
            return Optional.empty();
        } catch (RestClientException e) {
            String desc = String.format("An error has occurred in retrieving submission envelope %s : %s",
                    envelopeUuid.toString(), e.getMessage());
            String action = "Please ensure Ingest API is running.";
            handleException(desc, action, e);
        }
        return Optional.empty();
    }

    private StateMachine<SubmissionState, SubmissionEvent> updateState(StateMachine<SubmissionState, SubmissionEvent> stateMachine, SubmissionEnvelopeReference envelope) throws UnrecognisedSubmissionStateException {
        SubmissionState currentState = stateMachine.getState().getId();
        SubmissionState correctState = envelope.getState();

        // Note that some "ongoing" states like Draft/Validating/Invalid/Processing/Exporting can have extended states which
        // tracks some metadata in a submission. Please check StateMachineConfiguration class to check what these extended states are.
        // It would be more complicated to sync these extended states from core.
        // Syncing the state to a finished state like Valid, Submitted, Archived, Exported, Cleanup, Complete should be safe.
        // It might be best to redesign how we use the state machines and not have a separate state tracker.

        List<SubmissionState> statesWithoutExtendedStates = Arrays.asList(VALID, SUBMITTED, ARCHIVED, EXPORTED, CLEANUP, COMPLETE);

        if (!currentState.equals(correctState) && statesWithoutExtendedStates.contains(correctState)) {
            stateMachine.getStateMachineAccessor().doWithAllRegions(
                    access -> access.resetStateMachine(
                            new DefaultStateMachineContext<>(correctState, null, null, null, null, stateMachine.getId())
                    )
            );

            String envelopeUuid = envelope.getUuid();
            log.info(String.format("Synced %s from state %s to state: %s, with id: %s",
                    envelopeUuid, currentState, correctState, stateMachine.getId()));
        }
        return stateMachine;
    }

    private void deleteStateMachine(StateMachine<SubmissionState, SubmissionEvent> stateMachine) {
        Optional<RedisRepositoryStateMachine> redisStateMachine = stateMachineRepository.findById(stateMachine.getId());
        stateMachineRepository.delete(redisStateMachine.get());
        log.info(String.format("Deleted statemachine, id: %s, state:%s", stateMachine.getId(), stateMachine.getState().toString()));
    }

    private void handleException(String description, String action, Throwable e) {
        log.error("A failure in loading state machines has occurred.", e);
        throw new AutoLoaderFailureException(description, action, e);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadStateMachines();
    }
}
