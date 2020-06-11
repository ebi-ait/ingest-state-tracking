package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 21/03/2018.
 */
@Getter
public class DocumentCompletedMessage {
    DocumentCompletedMessage() {}

    private String documentId;
    private String envelopeUuid;
    private int documentIndex;
    private int totalDocuments;
}

// TODO Update manifest receiver complete message
// bundleIndex -> documentIndex
// totalBundles -> totalDocuments