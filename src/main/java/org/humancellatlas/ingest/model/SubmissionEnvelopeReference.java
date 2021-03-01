package org.humancellatlas.ingest.model;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.humancellatlas.ingest.state.SubmissionState;

import java.net.URI;
import java.util.UUID;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Getter
public class SubmissionEnvelopeReference {
    private final String id;
    private final String uuid;
    private SubmissionState state;
    private final URI callbackLocation;

    public SubmissionEnvelopeReference(String id, String uuid, SubmissionState state, URI callbackLocation) {
        this.id = id;
        this.uuid = uuid;
        this.state = state;
        this.callbackLocation = callbackLocation;
    }

    public SubmissionEnvelopeReference(String id, String uuid, URI callbackLocation) {
        this.id = id;
        this.uuid = uuid;
        this.callbackLocation = callbackLocation;
    }
}
