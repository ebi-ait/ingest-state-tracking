package org.humancellatlas.ingest.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.UUID;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Getter
@RequiredArgsConstructor
public class MetadataDocumentReference {
    private final String id;
    private final UUID uuid;
    private final URI callbackLocation;
}
