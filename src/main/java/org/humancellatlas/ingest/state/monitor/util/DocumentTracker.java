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
public class DocumentTracker {
    private int documentCount;
    private AtomicInteger completedDocumentCount;
    private Map<String, MetadataDocumentState> documentStateMap;

    public DocumentTracker() {}

    public DocumentTracker(int documentCount){
        this.documentCount = documentCount;
        this.documentStateMap = new ConcurrentHashMap<>();
        this.completedDocumentCount = new AtomicInteger();
    }

    public void setComplete(String documentId) {
        documentStateMap.put(documentId, MetadataDocumentState.COMPLETE);
        completedDocumentCount.incrementAndGet();
    }

    public void reset(int documentCount) {
        completedDocumentCount.set(0);
        this.documentCount = documentCount;
    }

    public void setProcessing(String documentId) {
        documentStateMap.put(documentId, MetadataDocumentState.PROCESSING);
    }

    public boolean allDocumentsCompleted() {
        return completedDocumentCount.get() >= documentCount;
    }

}
