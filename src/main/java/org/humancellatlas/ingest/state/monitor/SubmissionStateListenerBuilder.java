package org.humancellatlas.ingest.state.monitor;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by rolando on 15/02/2018.
 */
@Component
public class SubmissionStateListenerBuilder {
    private final SubmissionStateUpdater submissionStateUpdater;

    @Autowired
    public SubmissionStateListenerBuilder(SubmissionStateUpdater submissionStateUpdater) {
        this.submissionStateUpdater = submissionStateUpdater;
    }

    public SubmissionStateListener listenerFor(SubmissionEnvelopeReference submissionEnvelopeReference,
                                               SubmissionStateMonitor submissionStateMonitor,
                                               boolean autoRemove) {
        return new SubmissionStateListener(submissionEnvelopeReference, submissionStateMonitor, this.submissionStateUpdater, autoRemove);
    }
}
