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
    TEST_VALIDITY,
    SUBMISSION_REQUESTED,
    PROCESSING_STARTED,
    PROCESSING_FAILED,
    CLEANUP_STARTED,
    ALL_TASKS_COMPLETE
}
