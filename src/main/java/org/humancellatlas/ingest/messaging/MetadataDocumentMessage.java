package org.humancellatlas.ingest.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.humancellatlas.ingest.state.MetadataDocumentState;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Getter
@Setter
@AllArgsConstructor
public class MetadataDocumentMessage {
    MetadataDocumentMessage(){}

    private String documentType;
    private String documentId;
    private String documentUuid;
    private String callbackLink;
    private String validationState;
}
