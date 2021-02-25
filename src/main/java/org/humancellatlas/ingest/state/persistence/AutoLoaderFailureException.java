package org.humancellatlas.ingest.state.persistence;

import lombok.Getter;

public class AutoLoaderFailureException extends RuntimeException {
    @Getter
    private final String description;
    @Getter
    private final String action;

    public AutoLoaderFailureException(String description, String action, Throwable cause) {
        super(cause);
        this.description = description;
        this.action = action;
    }
}
