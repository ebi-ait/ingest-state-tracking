package org.humancellatlas.ingest.state;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public enum SubmissionEvent {
    CONTENT_ADDED,
    VALIDATION_STARTED,
    DOCUMENT_PROCESSED,
    SUBMISSION_REQUESTED,
    PROCESSING_STARTED,
    PROCESSING_FAILED,
    CLEANUP_STARTED,
    ALL_TASKS_COMPLETE;

    public static SubmissionEvent fromRequestedSubmissionState(SubmissionState state) {
        switch (state) {
            case SUBMITTED:
                return SUBMISSION_REQUESTED;
            case PROCESSING:
                return PROCESSING_STARTED;
            case CLEANUP:
                return CLEANUP_STARTED;
            case COMPLETE:
                return ALL_TASKS_COMPLETE;
            default:
                throw new RuntimeException(String.format("Cannot convert from user requested state %s to a state event", state.toString()));
        }
    }
}
