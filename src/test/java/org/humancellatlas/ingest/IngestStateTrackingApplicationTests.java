package org.humancellatlas.ingest;

import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.humancellatlas.ingest.testutil.MetadataDocumentEventBarrage;
import org.humancellatlas.ingest.testutil.MetadataDocumentTransitionLifecycle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
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
@RunWith(SpringRunner.class)
@SpringBootTest
public class IngestStateTrackingApplicationTests {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Autowired
    private SubmissionStateMonitor submissionStateMonitor;
    private SubmissionEnvelopeReference envelopeRef;
    private MetadataDocumentReference documentRef;

    @Before
    public void setup() {
        envelopeRef = new SubmissionEnvelopeReference("1234", UUID.randomUUID(),
                URI.create("http://localhost:8080/api/submissionEnvelopes/1234"));
        documentRef = new MetadataDocumentReference("5678", UUID.randomUUID(),
                URI.create("http://localhost:8080/api/metadataDocuments/5678"));
        submissionStateMonitor.monitorSubmissionEnvelope(envelopeRef, false);
    }

    @After
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

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending PROCESSING_STARTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.PROCESSING_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        log.debug("Sending CLEANUP_STARTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CLEANUP_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.CLEANUP, state);

        log.debug("Sending ALL_TASKS_COMPLETE event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ALL_TASKS_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.COMPLETE, state);
    }

    @Test
    public void testSuccessfulEventRunthroughWithBarrage() {
        MetadataDocumentEventBarrage barrage = new MetadataDocumentEventBarrage();

        // generate transition lifecycles for, say, 10 samples...
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

        // maybe 10 assays as well that go invalid and then valid
        for (int i = 0; i < 10; i++) {
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

        barrage.commence(submissionStateMonitor);

        SubmissionState state;

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending PROCESSING_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.PROCESSING_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        log.debug("Sending CLEANUP_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CLEANUP_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.CLEANUP, state);

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


    private MetadataDocumentReference generateMetadataDocumentReference() {
        int id = new Random().nextInt();
        return new MetadataDocumentReference(Integer.toString(id), UUID.randomUUID(),
                URI.create("http://localhost:8080/api/metadataDocuments/" + id));
    }

    private SubmissionEnvelopeReference generateSubmissionEnvelopeReference() {
        int id = new Random().nextInt();
        return new SubmissionEnvelopeReference(Integer.toString(id), UUID.randomUUID(),
                URI.create("http://localhost:8080/api/submissionEnvelopes/" + id));
    }

}
