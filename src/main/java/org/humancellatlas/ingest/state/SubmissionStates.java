package org.humancellatlas.ingest.state;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
public enum SubmissionStates {
    PENDING,
    DRAFT,
    VALIDATING,
    VALID,
    INVALID,
    SUBMITTED,
    PROCESSING,
    CLEANUP,
    COMPLETE
}
