package org.humancellatlas.ingest.state;

import org.humancellatlas.ingest.exception.UnrecognisedSubmissionStateException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    ARCHIVING,
    ARCHIVED,
    EXPORTING,
    EXPORTED,
    CLEANUP,
    COMPLETE,
    DOCUMENTS_WAITING,
    DOCUMENT_VALIDATION_STARTING,
    DOCUMENTS_VALIDATING,
    DOCUMENT_VALIDATION,
    DOCUMENTS_VALID,
    DOCUMENTS_INVALID,
    VALIDATION_STATE_EVAL_JUNCTION,
    PROCESSING_STATE_EVAL_JUNCTION,
    EXPORTING_STATE_EVAL_JUNCTION;

    private static final List<Set<SubmissionState>> ORDERED_STATES = Arrays.asList(
            new HashSet<>(Arrays.asList(PENDING)),
            new HashSet<>(Arrays.asList(DRAFT)),
            new HashSet<>(Arrays.asList(VALIDATING, VALID, INVALID, VALIDATION_STATE_EVAL_JUNCTION)),
            new HashSet<>(Arrays.asList(SUBMITTED)),
            new HashSet<>(Arrays.asList(PROCESSING, PROCESSING_STATE_EVAL_JUNCTION)),
            new HashSet<>(Arrays.asList(ARCHIVING)),
            new HashSet<>(Arrays.asList(ARCHIVED)),
            new HashSet<>(Arrays.asList(EXPORTING, EXPORTING_STATE_EVAL_JUNCTION)),
            new HashSet<>(Arrays.asList(EXPORTED)),
            new HashSet<>(Arrays.asList(CLEANUP)),
            new HashSet<>(Arrays.asList(COMPLETE))
    );

    /**
     * is this SubmissionState chronologically after that submissionState?
     *
     * @param submissionState
     * @return
     */
    public boolean after(SubmissionState submissionState) {
        int thisStateOrdering = 0;
        int thatStateOrdering = 0;

        for (int i = 0; i < ORDERED_STATES.size(); i++) {
            Set<SubmissionState> stateSet = ORDERED_STATES.get(i);
            if (stateSet.contains(this)) {
                thisStateOrdering = i;
            }

            if (stateSet.contains(submissionState)) {
                thatStateOrdering = i;
            }
        }

        return thisStateOrdering > thatStateOrdering;
    }

    public static SubmissionState fromString(String submissionState) throws UnrecognisedSubmissionStateException {
        switch (submissionState.toUpperCase()) {
            case "PENDING":
                return SubmissionState.PENDING;
            case "DRAFT":
                return SubmissionState.DRAFT;
            case "VALIDATING":
                return SubmissionState.VALIDATING;
            case "VALID":
                return SubmissionState.VALID;
            case "INVALID":
                return SubmissionState.INVALID;
            case "SUBMITTED":
                return SubmissionState.SUBMITTED;
            case "PROCESSING":
                return SubmissionState.PROCESSING;
            case "ARCHIVING":
                return SubmissionState.ARCHIVING;
            case "EXPORTING":
                return SubmissionState.EXPORTING;
            case "ARCHIVED":
                return SubmissionState.ARCHIVED;
            case "EXPORTED":
                return SubmissionState.EXPORTED;
            case "CLEANUP":
                return SubmissionState.CLEANUP;
            case "COMPLETE":
                return SubmissionState.COMPLETE;
            default:
                throw new UnrecognisedSubmissionStateException(String.format("The submission state %s is not recognised.", submissionState));
        }
    }
}
