package org.humancellatlas.ingest.state;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public enum DocumentEvents {
    CONTENT_ADDED,
    VALIDATION_STARTED,
    DOCUMENT_IS_VALID,
    DOCUMENT_IS_INVALID,
    PROCESSING_STARTED,
    PROCESSING_FAILED,
    ALL_TASKS_COMPLETE
}
