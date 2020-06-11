package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 21/03/2018.
 */
@Getter
public class DocumentCompletedMessage {
    DocumentCompletedMessage() {}

    private String envelopeUuid;
    private String documentId;
    private int index;
    private int total;
}