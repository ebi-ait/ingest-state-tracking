package org.humancellatlas.ingest;

import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by rolando on 12/03/2018.
 */
public class SubmissionEventEnumTest {

    @Test
    public void testEventFromRequestedState() {
        String requestedState = "Submitted";
        try {
            SubmissionEvent submittedEvent = SubmissionEvent.fromRequestedSubmissionState(SubmissionState.fromString(requestedState));
            assert true;
        } catch (Exception e) {
            assert false;
        }
    }

    @Test
    public void testFromString(){
        assertEquals(SubmissionState.fromString("Pending"), SubmissionState.PENDING);
        assertEquals(SubmissionState.fromString("Draft"), SubmissionState.DRAFT);
        assertEquals(SubmissionState.fromString("Metadata valid"), SubmissionState.METADATA_VALID);
        assertEquals(SubmissionState.fromString("Metadata invalid"), SubmissionState.METADATA_INVALID);
        assertEquals(SubmissionState.fromString("Metadata validating"), SubmissionState.METADATA_INVALID);
        assertEquals(SubmissionState.fromString("Metadata valid"), SubmissionState.METADATA_INVALID);
        assertEquals(SubmissionState.fromString("Graph valid"), SubmissionState.GRAPH_VALID);
        assertEquals(SubmissionState.fromString("Graph validation requested"), SubmissionState.GRAPH_VALIDATION_REQUESTED);
        assertEquals(SubmissionState.fromString("Submitted"), SubmissionState.SUBMITTED);
        assertEquals(SubmissionState.fromString("Exporting"), SubmissionState.EXPORTING);
        assertEquals(SubmissionState.fromString("Exported"), SubmissionState.EXPORTED);
        assertEquals(SubmissionState.fromString("Archiving"), SubmissionState.ARCHIVING);
        assertEquals(SubmissionState.fromString("Archived"), SubmissionState.ARCHIVED);
        assertEquals(SubmissionState.fromString("Cleanup"), SubmissionState.CLEANUP);
        assertEquals(SubmissionState.fromString("Complete"), SubmissionState.COMPLETE);

        assertEquals(MetadataDocumentState.fromString("Draft"));
    }
}
