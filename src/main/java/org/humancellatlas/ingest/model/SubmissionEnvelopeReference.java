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
@RequiredArgsConstructor
public class SubmissionEnvelopeReference {
    private final String id;
    private final String uuid;
    private final SubmissionState state;
    private final URI callbackLocation;
}
