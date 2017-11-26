package org.humancellatlas.ingest.state;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public enum SubmissionEvents {
    CONTENT_ADDED,
    VALIDATION_STARTED,
    ALL_DOCUMENTS_ARE_VALID,
    DOCUMENTS_ARE_INVALID,
    SUBMISSION_REQUESTED,
    PROCESSING_STARTED,
    PROCESSING_FAILED,
    CLEANUP_STARTED,
    ALL_TASKS_COMPLETE
}
