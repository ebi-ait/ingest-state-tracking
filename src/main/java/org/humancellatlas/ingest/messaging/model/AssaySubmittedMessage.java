package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 20/03/2018.
 */
@Getter
public class AssaySubmittedMessage{
    AssaySubmittedMessage(){}

    private String documentId;
    private String documentUuid;
    private String callbackLink;
    private String documentType;
    private String envelopeId;
    private String envelopeUuid;
    private int assayIndex;
    private int totalAssays;
}
