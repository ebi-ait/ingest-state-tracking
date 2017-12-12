package org.humancellatlas.ingest;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvents;
import org.humancellatlas.ingest.state.SubmissionStates;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
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
        submissionStateMonitor.monitorSubmissionEnvelope(envelopeRef);
    }

    @Test
    public void contextLoads() {

    }

    @Test
    public void testMonitoringOfNewEnvelope() {
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> stateMachine = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(stateMachine.isPresent());
    }

    @Test
    public void testEventDispatch() {
        UUID uuid = envelopeRef.getUuid();
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine = optional.get();
        assertEquals(SubmissionStates.PENDING, stateMachine.getState().getId());

        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CONTENT_ADDED);

        optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        stateMachine = optional.get();

        assertEquals(SubmissionStates.DRAFT, stateMachine.getState().getId());
    }

    @Test
    public void testSuccessfulEventRunthrough() {
        UUID uuid = envelopeRef.getUuid();
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine = optional.get();

        log.debug("Sending CONTENT_ADDED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CONTENT_ADDED);
        assertEquals(SubmissionStates.DRAFT, stateMachine.getState().getId());

        log.debug("Sending VALIDATION_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.VALIDATION_STARTED);
        assertEquals(SubmissionStates.VALIDATING, stateMachine.getState().getId());

        // wait for a bit
        try {
            TimeUnit.SECONDS.sleep(5);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        // now test validity
        log.debug("Sending TEST_VALIDITY event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.TEST_VALIDITY);
        assertEquals(SubmissionStates.VALID, stateMachine.getState().getId());


        log.debug("Sending SUBMISSION_REQUESTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.SUBMISSION_REQUESTED);
        assertEquals(SubmissionStates.SUBMITTED, stateMachine.getState().getId());

        log.debug("Sending PROCESSING_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.PROCESSING_STARTED);
        assertEquals(SubmissionStates.PROCESSING, stateMachine.getState().getId());

        log.debug("Sending CLEANUP_STARTED event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CLEANUP_STARTED);
        assertEquals(SubmissionStates.CLEANUP, stateMachine.getState().getId());

        log.debug("Sending ALL_TASKS_COMPLETE event");
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.ALL_TASKS_COMPLETE);
        assertEquals(SubmissionStates.COMPLETE, stateMachine.getState().getId());
    }

    @Test
    public void testIncorrectLifecycle() {
        UUID uuid = envelopeRef.getUuid();
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine = optional.get();

        // try to submit an invalid submission
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CONTENT_ADDED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.VALIDATION_STARTED);

        // now send an event that is wrong
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.SUBMISSION_REQUESTED);

        // check the state is invalid (i.e. last event wasn't accepted)
        assertEquals(SubmissionStates.INVALID, stateMachine.getState().getId());
    }
}
