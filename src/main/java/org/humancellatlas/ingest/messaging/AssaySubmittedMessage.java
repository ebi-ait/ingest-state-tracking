package org.humancellatlas.ingest.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by rolando on 20/03/2018.
 */
@Getter
@AllArgsConstructor
public class AssaySubmittedMessage{
    private final String documentId;
    private final String documentUuid;
    private final String callbackLink;
    private final String documentType;
    private final String envelopeId;
    private final String envelopeUuid;
    private final int assayIndex;
    private final int totalAssays;
}
