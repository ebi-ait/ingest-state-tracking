package org.humancellatlas.ingest.state.monitor.util;

import lombok.Getter;
import org.humancellatlas.ingest.state.MetadataDocumentState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by rolando on 21/03/2018.
 */
@Getter
public class AssayBundleTracker {
    private final int numAssaysExpected;
    private final AtomicInteger numAssaysProcessed;
    private final Map<String, MetadataDocumentState> assayBundleStateMap;
    private final String envelopeUuid;

    public AssayBundleTracker(int numAssaysExpected, String envelopeUuid){
        this.numAssaysExpected = numAssaysExpected;
        this.envelopeUuid = envelopeUuid;
        this.assayBundleStateMap = new ConcurrentHashMap<>();
        this.numAssaysProcessed = new AtomicInteger();
    }

    public void completedAssay(String assayId) {
        assayBundleStateMap.put(assayId, MetadataDocumentState.COMPLETE);
        numAssaysProcessed.incrementAndGet();
    }

    public void processingAssay(String assayId) {
        assayBundleStateMap.put(assayId, MetadataDocumentState.PROCESSING);
    }

    public boolean bundlesCompleted() {
        return numAssaysProcessed.get() == numAssaysExpected;
    }

}
