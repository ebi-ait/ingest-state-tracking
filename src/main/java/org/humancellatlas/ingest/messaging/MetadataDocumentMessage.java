package org.humancellatlas.ingest.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Getter
@AllArgsConstructor
public class MetadataDocumentMessage {
    private final String documentType;
    private final String documentId;
    private final String documentUuid;
    private final String callbackLink;
}
