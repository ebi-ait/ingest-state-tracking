package org.humancellatlas.ingest.client.util;

import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by rolando on 19/03/2018.
 */
public class EnvelopeReferenceCache extends LinkedHashMap<String, SubmissionEnvelopeReference> {
    private int capacity;

    public EnvelopeReferenceCache(int capacity) {
        super(capacity+1, 1.0f, true);
        this.capacity = capacity;
    }

    protected boolean removeEldestEntry(Map.Entry<String, SubmissionEnvelopeReference> eldestEntry) {
        return (size() > this.capacity);
    }
}
