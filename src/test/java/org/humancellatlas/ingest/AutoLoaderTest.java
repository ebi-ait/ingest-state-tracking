package org.humancellatlas.ingest;

import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.*;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.humancellatlas.ingest.state.persistence.AutoLoader;
import org.humancellatlas.ingest.state.persistence.Persister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.data.StateMachineRepository;
import org.springframework.statemachine.data.redis.RedisRepositoryStateMachine;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.util.*;


import static org.assertj.core.api.Assertions.assertThat;
import static org.humancellatlas.ingest.state.SubmissionState.*;
import static org.humancellatlas.ingest.state.SubmissionState.COMPLETE;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {AutoLoader.class, StateMachineConfiguration.class })
public class AutoLoaderTest {
    @Autowired
    private AutoLoader autoLoader;

    @Autowired
    private StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory;

    @MockBean
    private SubmissionStateMonitor submissionStateMonitor;

    @MockBean
    private Persister persister;

    @MockBean
    private IngestApiClient ingestApiClient;

    @MockBean
    private StateMachineRepository<RedisRepositoryStateMachine> stateMachineRepository;

    private UUID submissionUuid = UUID.randomUUID();

    StateMachine<SubmissionState, SubmissionEvent> stateMachine;

    @BeforeEach
    public void setup() {

        List<StateMachine<SubmissionState, SubmissionEvent>> stateMachines = new ArrayList<>();
        stateMachine = stateMachineFactory.getStateMachine(submissionUuid.toString());
        Message<SubmissionEvent> message = MessageBuilder.withPayload(SubmissionEvent.DOCUMENT_PROCESSED)
                .setHeader(MetadataDocumentInfo.DOCUMENT_ID, "id")
                .setHeader(MetadataDocumentInfo.DOCUMENT_STATE, MetadataDocumentState.DRAFT)
                .build();
        stateMachine.start();
        stateMachine.sendEvent(message);
        stateMachines.add(stateMachine);

        when(persister.retrieveStateMachines()).thenReturn(stateMachines);

    }

    @Test
    public void testAutoLoadShouldRetrieveAndMonitor(){
        // given
        SubmissionEnvelopeReference submission = new SubmissionEnvelopeReference("id", submissionUuid.toString(), "Draft", URI.create("/callback") );
        when(ingestApiClient.referenceForSubmissionEnvelope(submissionUuid)).thenReturn(submission);

        // when
        autoLoader.autoLoad();

        // then
        verify(submissionStateMonitor).monitorSubmissionEnvelope(submission, stateMachine);
        assertThat(stateMachine.getState().getId()).isEqualTo(SubmissionState.DRAFT);
    }

    @Test
    public void testAutoLoadShouldUpdateStateWhenCorrectStateIsAFinishedState(){
        List<String> finishedStates = Arrays.asList("Valid", "Submitted", "Archived", "Exported", "Cleanup", "Completed");

        finishedStates.forEach(state -> {
            // given
            SubmissionEnvelopeReference submission = new SubmissionEnvelopeReference("id", submissionUuid.toString(), state, URI.create("/callback") );
            when(ingestApiClient.referenceForSubmissionEnvelope(submissionUuid)).thenReturn(submission);

            // when
            autoLoader.autoLoad();

            // then
            verify(submissionStateMonitor).monitorSubmissionEnvelope(submission, stateMachine);
            assertThat(stateMachine.getState().getId()).isEqualTo(SubmissionState.fromString(state));
        });
    }

    @Test
    public void testAutoLoadShouldNotUpdateStateWhenCorrectStateIsNotAFinishedState(){

        List<String> finishedStates = Arrays.asList("Draft", "Validating", "Invalid", "Processing", "Exporting", "Archiving");

        finishedStates.forEach(state -> {
            // given
            SubmissionEnvelopeReference submission = new SubmissionEnvelopeReference("id", submissionUuid.toString(), state, URI.create("/callback") );
            when(ingestApiClient.referenceForSubmissionEnvelope(submissionUuid)).thenReturn(submission);

            // when
            autoLoader.autoLoad();

            // then
            verify(submissionStateMonitor).monitorSubmissionEnvelope(submission, stateMachine);
            assertThat(stateMachine.getState().getId()).isEqualTo(SubmissionState.DRAFT);
        });

    }

    @Test
    public void testAutoLoadShouldDeleteStateMachine(){
        // given
        when(ingestApiClient.referenceForSubmissionEnvelope(submissionUuid))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null));

        RedisRepositoryStateMachine redisStateMachine = mock(RedisRepositoryStateMachine.class);
        when(stateMachineRepository.findById(anyString())).thenReturn(Optional.of(redisStateMachine));

        // when
        autoLoader.autoLoad();

        // then
        verify(submissionStateMonitor,  never()).monitorSubmissionEnvelope(any());
        verify(stateMachineRepository).delete(redisStateMachine);
    }


}
