package org.humancellatlas.ingest;

import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateListenerBuilder;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.humancellatlas.ingest.state.persistence.Persister;
import org.humancellatlas.ingest.state.persistence.RedisPersister;
import org.humancellatlas.ingest.testutil.MetadataDocumentEventBarrage;
import org.humancellatlas.ingest.testutil.MetadataDocumentTransitionLifecycle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.*;

/**
 * Created by rolando on 14/05/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = {"app.auto-persist.enable=false", "app.auto-load.enable=false"})
@ActiveProfiles("redis-persistence")
public class RedisPersisterTest {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Autowired
    private StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory;
    @Autowired
    private Persister redisPersister;
    @Autowired
    private SubmissionStateMonitor submissionStateMonitor;

    private SubmissionEnvelopeReference envelopeRef;

    @Before
    public void setup() {
        envelopeRef = new SubmissionEnvelopeReference("1234", UUID.randomUUID().toString(),
                                                      URI.create("http://localhost:8080/api/submissionEnvelopes/1234"));

        submissionStateMonitor.monitorSubmissionEnvelope(envelopeRef, false);

    }

    @Test
    public void testPersistStateMachine() {
        redisPersister.deleteAllStateMachines();

        StateMachine<SubmissionState, SubmissionEvent> stateMachine = stateMachineFactory.getStateMachine(envelopeRef.getUuid());
        stateMachine.start();
        redisPersister.persistStateMachines(Collections.singletonList(stateMachine));


        assert redisPersister.retrieveStateMachines().size() == 1;
    }

    @Test
    public void testPersistStateMachines() {
        redisPersister.deleteAllStateMachines();

        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 100 samples...
        for (int i = 0; i < 100; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .build();

            barrage.addToBarrage(sampleTransitionLifecycle);
        }

        barrage.commence(submissionStateMonitor);

        redisPersister.persistStateMachines(submissionStateMonitor.getStateMachines());

        Collection<StateMachine<SubmissionState, SubmissionEvent>> persistedMachines =
                redisPersister.retrieveStateMachines();

        assert persistedMachines.size() == 1;

        // stop monitoring the state machine, reload it from redis, and point another barrage at it
        submissionStateMonitor.stopMonitoring(envelopeRef);
        submissionStateMonitor.loadStateMachines(redisPersister.retrieveStateMachines());

        for (int i = 0; i < 50; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.INVALID)
                    .build();

            barrage.addToBarrage(sampleTransitionLifecycle);
        }

        barrage.commence(submissionStateMonitor);

        assert submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.INVALID);

    }

    private MetadataDocumentReference generateMetadataDocumentReference() {
        int id = new Random().nextInt();
        return new MetadataDocumentReference(Integer.toString(id), UUID.randomUUID().toString(),
                                             URI.create("http://localhost:8080/api/metadataDocuments/" + id));
    }
}
