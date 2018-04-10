package org.humancellatlas.ingest.messaging.model;

import lombok.Getter;

/**
 * Created by rolando on 21/03/2018.
 */
@Getter
public class BundleCompletedMessage {
    BundleCompletedMessage() {}

    private String documentId;
    private String envelopeUuid;
    private int bundleIndex;
    private int totalBundles;
}
