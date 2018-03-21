package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 21/03/2018.
 */
@Getter
public class AssayCompletedMessage {
    AssayCompletedMessage() {}

    private String documentId;
    private String envelopeUuid;
    private int assayIndex;
    private int totalAssays;
}
