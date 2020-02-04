package org.humancellatlas.ingest;

import org.humancellatlas.ingest.messaging.Constants;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.humancellatlas.ingest.testutil.MetadataDocumentEventBarrage;
import org.humancellatlas.ingest.testutil.MetadataDocumentTransitionLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigInteger;
import java.net.URI;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class IngestStateTrackingApplicationTests {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Autowired
    private SubmissionStateMonitor submissionStateMonitor;
    private SubmissionEnvelopeReference envelopeRef;
    private MetadataDocumentReference documentRef;

    @BeforeEach
    public void setup() {
        envelopeRef = new SubmissionEnvelopeReference("1234", UUID.randomUUID().toString(),
                URI.create("http://localhost:8080/api/submissionEnvelopes/1234"));
        documentRef = new MetadataDocumentReference("5678", UUID.randomUUID().toString(),
                URI.create("http://localhost:8080/api/metadataDocuments/5678"));
        submissionStateMonitor.monitorSubmissionEnvelope(envelopeRef, false);
    }

    @AfterEach
    public void cleanup() {
        submissionStateMonitor.stopMonitoring(envelopeRef);
    }

    @Test
    public void contextLoads() {

    }

    @Test
    public void testMonitoringOfNewEnvelope() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
    }

    @Test
    public void testEventDispatch() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
        SubmissionState state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PENDING, state);

        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);
    }

    @Test
    public void testSuccessfulEventRunthrough() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
        SubmissionState state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PENDING, state);

        log.debug("Adding content");
        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.VALIDATING, state);

        // wait for a bit to simulate validation happening
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALID);
        // wait for a bit to allow propagation of cascade events
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.VALID, state);

        // assert 0 documents in extended state map
        Map<String, MetadataDocumentState> metadataDocumentStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentStateMap.entrySet().size() == 0);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending BUNDLE_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfBundleState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        log.debug("Sending BUNDLE_STATE_UPDATE event for a completed assay");
        submissionStateMonitor.notifyOfBundleState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVING, state);
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ARCHIVING_COMPLETE);
        log.debug("Sending ALL_TASKS_COMPLETE event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ALL_TASKS_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.COMPLETE, state);
    }

    @Test
    public void testSuccessfulEventRunthroughWithBarrage() {
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

        // maybe 100 assays as well that go invalid and then valid
        for (int i = 0; i < 100; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle assayTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.INVALID)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .build();

            barrage.addToBarrage(assayTransitionLifecycle);
        }

        // 200 files
        for (int i = 0; i < 100; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle assayTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.INVALID)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .build();

            barrage.addToBarrage(assayTransitionLifecycle);
        }

        barrage.commence(submissionStateMonitor);

        SubmissionState state;

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);


        // send mock events about assays submitted for processing to bundles
        int expectedAssays = 5;
        String mockAssayDocumentId = "mock-assay-id";

        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfBundleState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.PROCESSING);
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        // mock events for bundled/completed assays
        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfBundleState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.COMPLETE);
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVING, state);
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ARCHIVING_COMPLETE);
        log.debug("Sending ALL_TASKS_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ALL_TASKS_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.COMPLETE, state);
    }

    @Test
    public void testUnsuccessfulBarrage_OneDocumentNotValid() {
        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 15 samples...
        for (int i = 0; i < 15; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .build();

            barrage.addToBarrage(sampleTransitionLifecycle);
        }

        //...and one piece of metadata that goes from valid to invalid
        MetadataDocumentReference documentReference = generateMetadataDocumentReference();
        MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                .Builder(documentReference, envelopeRef)
                .addStateTransition(MetadataDocumentState.DRAFT)
                .addStateTransition(MetadataDocumentState.VALIDATING)
                .addStateTransition(MetadataDocumentState.VALID)
                .addStateTransition(MetadataDocumentState.DRAFT)
                .addStateTransition(MetadataDocumentState.VALIDATING)
                .addStateTransition(MetadataDocumentState.INVALID)
                .build();

        barrage.addToBarrage(sampleTransitionLifecycle);

        barrage.commence(submissionStateMonitor);

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.INVALID));
    }

    @Test
    public void testIncorrectLifecycle() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));

        // try to submit an invalid submission
        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);

        // now send an event that is wrong
        boolean eventResponse = submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        assertFalse(eventResponse);
    }

    @Test
    public void testHandlingOfRedundantStateInfoMessages() {
        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // test how it handles messages e.g draft -> draft -> draft -> draft -> validating -> valid
        for (int i = 0; i < 8; i++) {
            MetadataDocumentReference documentReference = generateMetadataDocumentReference();
            MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                    .Builder(documentReference, envelopeRef)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.DRAFT)
                    .addStateTransition(MetadataDocumentState.VALIDATING)
                    .addStateTransition(MetadataDocumentState.VALID)
                    .build();

            barrage.addToBarrage(sampleTransitionLifecycle);
        }

        barrage.commence(submissionStateMonitor);

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.VALID));
    }

    @Test
    public void testTransitionsBackToDraftWhenValidAndNewDocumentAdded() {
        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 15 samples...
        for (int i = 0; i < 10; i++) {
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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.VALID));

        // add a draft document
        barrage = new MetadataDocumentEventBarrage();
        MetadataDocumentReference documentReference = generateMetadataDocumentReference();
        MetadataDocumentTransitionLifecycle sampleTransitionLifecycle = new MetadataDocumentTransitionLifecycle
                .Builder(documentReference, envelopeRef)
                .addStateTransition(MetadataDocumentState.DRAFT)
                .build();

        barrage.addToBarrage(sampleTransitionLifecycle);

        barrage.commence(submissionStateMonitor);

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.DRAFT));
    }

    @Test
    public void testDoesNotTransitionBackToValidAfterSubmitted() {
        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 15 samples...
        for (int i = 0; i < 10; i++) {
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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.VALID));

        SubmissionState state;

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 15 samples...
        for (int i = 0; i < 10; i++) {
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

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);
    }

    @Test
    public void testSubmissionStateOrdering() {
        assertTrue(SubmissionState.DRAFT.after(SubmissionState.valueOf("pEnDing".toUpperCase())));
        assertTrue(SubmissionState.SUBMITTED.after(SubmissionState.VALID));
        assertTrue(SubmissionState.PROCESSING.after(SubmissionState.VALID));
        assertTrue(SubmissionState.CLEANUP.after(SubmissionState.VALID));
        assertTrue(SubmissionState.COMPLETE.after(SubmissionState.VALID));
    }

    private MetadataDocumentReference generateMetadataDocumentReference() {
        int id = new Random().nextInt();
        return new MetadataDocumentReference(Integer.toString(id), UUID.randomUUID().toString(),
                URI.create("http://localhost:8080/api/metadataDocuments/" + id));
    }

    private SubmissionEnvelopeReference generateSubmissionEnvelopeReference() {
        int id = new Random().nextInt();
        return new SubmissionEnvelopeReference(Integer.toString(id), UUID.randomUUID().toString(),
                URI.create("http://localhost:8080/api/submissionEnvelopes/" + id));
    }


    @Test
    public void testThreadAssigner() {
        String[] resourceIds = {
                "abcdef12345abab78903a",
                "abcdef12345abab78903b",
                "abcdef12345abab78903c",
                "abcdef12345abab78903d",
                "abcdef12345abab78903e",
                "abcdef12345abab78903f"
        };

        int numWorkers = 6;
        for (int i = 0; i < numWorkers; i++) {
            String resourceId = resourceIds[i];
            BigInteger resourceValue = new BigInteger(resourceId, 16); // assuming resource is hex
            int index = resourceValue.mod(BigInteger.valueOf(numWorkers)).intValue();
            int h = 2222;
        }
    }
}
