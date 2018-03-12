package org.humancellatlas.ingest;

import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.junit.Test;

/**
 * Created by rolando on 12/03/2018.
 */
public class SubmissionEventEnumTest {

    @Test
    public void testEventFromRequestedState() {
        String requestedState = "Submitted";
        try {
            SubmissionEvent submittedEvent = SubmissionEvent.fromRequestedSubmissionState(SubmissionState.valueOf(requestedState.toUpperCase()));
            assert true;
        } catch (Exception e) {
            assert false;
        }
    }
}
