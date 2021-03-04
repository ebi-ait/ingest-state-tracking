package org.humancellatlas.ingest.exception;

public class UnrecognisedSubmissionStateException extends RuntimeException {

    public UnrecognisedSubmissionStateException(String message) {
        super(message);
    }

    public UnrecognisedSubmissionStateException(String message, Throwable e) {
        super(message, e);
    }
}
