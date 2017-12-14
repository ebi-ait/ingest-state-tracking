package org.humancellatlas.ingest.state;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public enum SubmissionState {
    PENDING,
    DRAFT,
    VALIDATING,
    VALID,
    INVALID,
    SUBMITTED,
    PROCESSING,
    CLEANUP,
    COMPLETE,
    DOCUMENTS_WAITING,
    DOCUMENT_VALIDATION_STARTING,
    DOCUMENTS_VALIDATING,
    DOCUMENT_VALIDATION,
    DOCUMENTS_VALID,
    DOCUMENTS_INVALID
}
