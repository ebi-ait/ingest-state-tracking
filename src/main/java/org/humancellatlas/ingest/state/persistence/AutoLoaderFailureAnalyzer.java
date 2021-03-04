package org.humancellatlas.ingest.state.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

public class AutoLoaderFailureAnalyzer extends AbstractFailureAnalyzer<AutoLoaderFailureException> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, AutoLoaderFailureException failure) {
        return new FailureAnalysis(failure.getDescription(), failure.getAction(), failure.getCause());
    }
}
