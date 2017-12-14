package org.humancellatlas.ingest;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
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
    @Autowired private SubmissionStateMonitor submissionStateMonitor;

    private SubmissionEnvelopeReference envelopeRef;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Before
    public void setup() {
        envelopeRef = new SubmissionEnvelopeReference("1234", UUID.randomUUID(), URI.create("http://localhost:8080/api/submissionEnvelopes/1234"));
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
        Optional<SubmissionState> stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.PENDING, stateOpt.get());

        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CONTENT_ADDED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.DRAFT, stateOpt.get());
    }

    @Test
    public void testSuccessfulEventRunthrough() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));
        Optional<SubmissionState> stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.PENDING, stateOpt.get());

        log.debug("Sending CONTENT_ADDED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CONTENT_ADDED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.DRAFT, stateOpt.get());

        log.debug("Sending VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.VALIDATION_STARTED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.VALIDATING, stateOpt.get());

        // wait for a bit
        try {
            TimeUnit.SECONDS.sleep(5);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        // now test validity
        log.debug("Sending TEST_VALIDITY event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.TEST_VALIDITY);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.VALID, stateOpt.get());


        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.SUBMITTED, stateOpt.get());

        log.debug("Sending PROCESSING_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.PROCESSING_STARTED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.PROCESSING, stateOpt.get());

        log.debug("Sending CLEANUP_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CLEANUP_STARTED);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.CLEANUP, stateOpt.get());

        log.debug("Sending ALL_TASKS_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.ALL_TASKS_COMPLETE);
        stateOpt = submissionStateMonitor.findCurrentState(envelopeRef);
        assertTrue(stateOpt.isPresent());
        assertEquals(SubmissionState.COMPLETE, stateOpt.get());
    }

    @Test
    public void testIncorrectLifecycle() {
        assertTrue(submissionStateMonitor.isMonitoring(envelopeRef));

        // try to submit an invalid submission
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.CONTENT_ADDED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.VALIDATION_STARTED);

        // now send an event that is wrong
        Optional<Boolean> eventOpt = submissionStateMonitor.sendEventForSubmissionEnvelope(envelopeRef, SubmissionEvent.SUBMISSION_REQUESTED);
        assertTrue(eventOpt.isPresent());
        assertFalse(eventOpt.get());
    }
}
