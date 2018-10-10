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
public class BundleTracker {
    private int numBundlesExpected;
    private AtomicInteger numBundlesProcessed;
    private Map<String, MetadataDocumentState> bundleableProcessStateMap;
    private String envelopeUuid;

    public BundleTracker() {}

    public BundleTracker(int numBundlesExpected, String envelopeUuid){
        this.numBundlesExpected = numBundlesExpected;
        this.envelopeUuid = envelopeUuid;
        this.bundleableProcessStateMap = new ConcurrentHashMap<>();
        this.numBundlesProcessed = new AtomicInteger();
    }

    public void completedBundle(String bundleableProcessId) {
        bundleableProcessStateMap.put(bundleableProcessId, MetadataDocumentState.COMPLETE);
        numBundlesProcessed.incrementAndGet();
    }

    public void processingAssay(String bundleableProcessId) {
        bundleableProcessStateMap.put(bundleableProcessId, MetadataDocumentState.PROCESSING);
    }

    public boolean bundlesCompleted() {
        return numBundlesProcessed.get() >= numBundlesExpected;
    }

}
