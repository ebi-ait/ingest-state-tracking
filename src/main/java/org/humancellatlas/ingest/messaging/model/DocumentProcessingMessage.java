package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 20/03/2018.
 */
@Getter
public class DocumentProcessingMessage {
    DocumentProcessingMessage(){}

    private String documentId;
    private String documentUuid;
    private String callbackLink;
    private String documentType;
    private String envelopeId;
    private String envelopeUuid;
    private int index;
    private int total;
}
