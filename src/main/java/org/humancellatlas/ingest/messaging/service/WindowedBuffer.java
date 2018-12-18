package org.humancellatlas.ingest.messaging.service;

public interface WindowedBuffer<T> {
    void flush(float flushPercent);
    void add(T message);
}
