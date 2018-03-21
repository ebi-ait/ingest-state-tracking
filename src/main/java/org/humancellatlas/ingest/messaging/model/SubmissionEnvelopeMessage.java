package org.humancellatlas.ingest.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.humancellatlas.ingest.state.SubmissionState;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Getter
@AllArgsConstructor
public class SubmissionEnvelopeMessage {
    SubmissionEnvelopeMessage() {}

    private String documentId;
    private String documentUuid;
    private String callbackLink;
    private String requestedState;
}
