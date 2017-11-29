package org.humancellatlas.ingest;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionEvents;
import org.humancellatlas.ingest.state.SubmissionStates;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.statemachine.StateMachine;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

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
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CONTENT_ADDED);
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine = optional.get();
        assertEquals(SubmissionStates.DRAFT, stateMachine.getState().getId());
    }

    @Test
    public void testSuccessfulEventRunthrough() {
        UUID uuid = envelopeRef.getUuid();
        Optional<StateMachine<SubmissionStates, SubmissionEvents>> optional = submissionStateMonitor.findStateMachine(envelopeRef.getUuid());
        assertTrue(optional.isPresent());
        StateMachine<SubmissionStates, SubmissionEvents> stateMachine = optional.get();

        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CONTENT_ADDED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.VALIDATION_STARTED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.VALIDATION_STARTED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.SUBMISSION_REQUESTED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.PROCESSING_STARTED);
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.CLEANUP_STARTED);
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
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.VALIDATION_STARTED);

        // now send an event that is wrong
        submissionStateMonitor.sendEventForSubmissionEnvelope(uuid, SubmissionEvents.SUBMISSION_REQUESTED);

        // check the state is invalid (i.e. last event wasn't accepted)
        assertEquals(SubmissionStates.INVALID, stateMachine.getState().getId());
    }
}
