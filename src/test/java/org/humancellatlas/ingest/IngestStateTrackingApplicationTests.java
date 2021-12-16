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
import java.util.*;
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
        envelopeRef = new SubmissionEnvelopeReference("1234", UUID.randomUUID().toString(), SubmissionState.PENDING,
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
        assertEquals(SubmissionState.METADATA_VALIDATING, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);

        // assert 0 documents in extended state map
        Map<String, MetadataDocumentState> metadataDocumentStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentStateMap.entrySet().size() == 0);

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending PROCESSING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.PROCESSING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        log.debug("Sending PROCESSING_STATE_UPDATE event for a completed assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.PROCESSING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVING, state);
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ARCHIVING_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
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
    public void testSuccessfulEventRunthroughWithoutArchiving() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
        SubmissionState state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PENDING, state);

        log.debug("Adding content");
        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.METADATA_VALIDATING, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);

        // assert 0 documents in extended state map
        Map<String, MetadataDocumentState> metadataDocumentStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentStateMap.entrySet().size() == 0);

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
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
    public void testSuccessfulEventRunthroughAfterUpdate() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
        SubmissionState state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PENDING, state);

        log.debug("Adding content");
        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.METADATA_VALIDATING, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);

        // assert 0 documents in extended state map
        Map<String, MetadataDocumentState> metadataDocumentStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentStateMap.entrySet().size() == 0);

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

        // SCENARIO: Add more content, this time adding an extra metadata document
        log.debug("Adding content");

        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.METADATA_VALIDATING, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);

        // assert 0 documents in extended state map
        Map<String, MetadataDocumentState> metadataDocumentUpdatedStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentUpdatedStateMap.entrySet().size() == 0);

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Adding a second assay the second time around");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id-2",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);


        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        assertEquals(SubmissionState.EXPORTING, submissionStateMonitor.findCurrentState(envelopeRef));


        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id-3",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        submissionStateMonitor.notifyOfDocumentState("mock-assay-id-2",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);


        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id-3",
                envelopeRef.getUuid(),
                3,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

        // SCENARIO: Add content again for re-export, this time removing the second metadata document
        log.debug("Adding content");

        assertTrue(submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT));
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.DRAFT, state);

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.METADATA_VALIDATING, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);

        // assert 0 documents in extended state map
        metadataDocumentUpdatedStateMap = (Map<String, MetadataDocumentState>) submissionStateMonitor.findStateMachine(UUID.fromString(envelopeRef.getUuid())).get().getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
        assertTrue(metadataDocumentUpdatedStateMap.entrySet().size() == 0);

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor
                .sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        assertEquals(SubmissionState.EXPORTED, submissionStateMonitor.findCurrentState(envelopeRef));
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

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);


        // send mock events about assays submitted for processing manifests
        int expectedAssays = 5;
        String mockAssayDocumentId = "mock-assay-id";

        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfDocumentState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.PROCESSING, SubmissionEvent.PROCESSING_STATE_UPDATE);
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.PROCESSING, state);

        // mock events for bundled/completed assays
        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfDocumentState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.COMPLETE, SubmissionEvent.PROCESSING_STATE_UPDATE);
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVING, state);
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ARCHIVING_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.ARCHIVED, state);

        // send mock events about assays submitted for exporting experiments
        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfDocumentState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);
        }

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        // mock events for bundled/completed assays
        for (int i = 0; i < expectedAssays; i++) {
            submissionStateMonitor.notifyOfDocumentState(mockAssayDocumentId + i,
                    envelopeRef.getUuid(),
                    expectedAssays,
                    MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);
        }
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

        log.debug("Sending CLEANUP_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CLEANUP_STARTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.CLEANUP, state);

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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.METADATA_INVALID));
    }

    @Test
    public void testIncorrectLifecycle() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);

        // Send events that are wrong
        List.of(
            SubmissionEvent.SUBMISSION_REQUESTED,
            SubmissionEvent.GRAPH_VALIDATION_STARTED,
            SubmissionEvent.GRAPH_VALIDATION_PROCESSING,
            SubmissionEvent.GRAPH_VALIDATION_COMPLETE,
            SubmissionEvent.GRAPH_VALIDATION_INVALID).forEach(event -> {
                boolean response = submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, event);
                assertFalse(response);
        });
    }

    @Test
    public void testGraphValidBackToDraft() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);
        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);

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

        assertEquals(SubmissionState.METADATA_VALID, submissionStateMonitor.findCurrentState(envelopeRef));

        List.of(
                List.of(SubmissionEvent.GRAPH_VALIDATION_STARTED, SubmissionState.GRAPH_VALIDATION_REQUESTED),
                List.of(SubmissionEvent.GRAPH_VALIDATION_PROCESSING, SubmissionState.GRAPH_VALIDATING),
                List.of(SubmissionEvent.GRAPH_VALIDATION_COMPLETE, SubmissionState.GRAPH_VALID)
        ).forEach(eventAndState -> {
            boolean response = submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, (SubmissionEvent) eventAndState.get(0));
            assertTrue(response);
            assertEquals(eventAndState.get(1), submissionStateMonitor.findCurrentState(envelopeRef));
        });

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);
        assertEquals(SubmissionState.DRAFT, submissionStateMonitor.findCurrentState(envelopeRef));
    }

    @Test
    public void testGraphInvalidBackToDraft() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);
        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.VALIDATING);

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

        assertEquals(SubmissionState.METADATA_VALID, submissionStateMonitor.findCurrentState(envelopeRef));

        List.of(
                List.of(SubmissionEvent.GRAPH_VALIDATION_STARTED, SubmissionState.GRAPH_VALIDATION_REQUESTED),
                List.of(SubmissionEvent.GRAPH_VALIDATION_PROCESSING, SubmissionState.GRAPH_VALIDATING),
                List.of(SubmissionEvent.GRAPH_VALIDATION_INVALID, SubmissionState.GRAPH_INVALID)
        ).forEach(eventAndState -> {
            boolean response = submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, (SubmissionEvent) eventAndState.get(0));
            assertTrue(response);
            assertEquals(eventAndState.get(1), submissionStateMonitor.findCurrentState(envelopeRef));
        });

        submissionStateMonitor.notifyOfMetadataDocumentState(documentRef, envelopeRef, MetadataDocumentState.DRAFT);
        assertEquals(SubmissionState.DRAFT, submissionStateMonitor.findCurrentState(envelopeRef));
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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.METADATA_VALID));
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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.METADATA_VALID));

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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.METADATA_VALID));

        SubmissionState state;

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);
    }

    @Test
    public void testDoesNotTransitionBackToValidAfterExported() {
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

        assertTrue(submissionStateMonitor.findCurrentState(envelopeRef).equals(SubmissionState.METADATA_VALID));

        SubmissionState state;

        log.debug("Sending GRAPH_VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_STARTED);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATION_REQUESTED, state);

        log.debug("Sending GRAPH_VALIDATION_PROCESSING event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_PROCESSING);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALIDATING, state);

        log.debug("Sending GRAPH_VALIDATION_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.GRAPH_VALIDATION_COMPLETE);
        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.GRAPH_VALID, state);

        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.SUBMITTED, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a submitted assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.PROCESSING, SubmissionEvent.EXPORTING_STATE_UPDATE);

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTING, state);

        log.debug("Sending EXPORTING_STATE_UPDATE event for a completed assay");
        submissionStateMonitor.notifyOfDocumentState("mock-assay-id",
                envelopeRef.getUuid(),
                1,
                MetadataDocumentState.COMPLETE, SubmissionEvent.EXPORTING_STATE_UPDATE);

        barrage = new MetadataDocumentEventBarrage();

        state = submissionStateMonitor.findCurrentState(envelopeRef);
        assertEquals(SubmissionState.EXPORTED, state);

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
        assertEquals(SubmissionState.METADATA_VALID, state);
    }

    @Test
    public void testSubmissionStateOrdering() {
        assertTrue(SubmissionState.DRAFT.after(SubmissionState.fromString("pEnDing")));
        assertTrue(SubmissionState.SUBMITTED.after(SubmissionState.METADATA_VALID));
        assertTrue(SubmissionState.PROCESSING.after(SubmissionState.METADATA_VALID));
        assertTrue(SubmissionState.CLEANUP.after(SubmissionState.METADATA_VALID));
        assertTrue(SubmissionState.COMPLETE.after(SubmissionState.METADATA_VALID));
    }

    private MetadataDocumentReference generateMetadataDocumentReference() {
        int id = new Random().nextInt();
        return new MetadataDocumentReference(Integer.toString(id), UUID.randomUUID().toString(),
                URI.create("http://localhost:8080/api/metadataDocuments/" + id));
    }

    private SubmissionEnvelopeReference generateSubmissionEnvelopeReference() {
        int id = new Random().nextInt();
        return new SubmissionEnvelopeReference(Integer.toString(id), UUID.randomUUID().toString(), SubmissionState.PENDING,
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
