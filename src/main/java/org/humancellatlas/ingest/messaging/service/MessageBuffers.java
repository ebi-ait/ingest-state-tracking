package org.humancellatlas.ingest.messaging.service;

import lombok.Data;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Component
public class MessageBuffers {
    private final DocumentUpdateBuffer documentUpdateBuffer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MessageBuffers(@Autowired MessageHandler messageHandler) {
        this.documentUpdateBuffer = new DocumentUpdateBuffer(messageHandler, TimeUnit.SECONDS.toMillis(5));
        this.initFlusher();
    }

    public void addDocumentUpdateMessage(MetadataDocumentMessage message) {
        this.documentUpdateBuffer.add(message);
    }


    static abstract class AbstractMessageBuffer<T> implements WindowedBuffer<T>{
        private final BlockingQueue<QueuedMessage<T>> messageQueue = new DelayQueue<>();
        private final MessageHandler messageHandler;
        private final long waitTime;

        AbstractMessageBuffer(MessageHandler messageHandler, long waitTime) {
            this.messageHandler = messageHandler;
            this.waitTime = waitTime;
        }

        private Stream<QueuedMessage<T>> bufferedMessageStream() {
            Queue<QueuedMessage<T>> drainedQueue = new PriorityQueue<>(Comparator.comparing(QueuedMessage::getIntendedStartTime));
            this.messageQueue.drainTo(drainedQueue);
            return Stream.generate(drainedQueue::remove)
                         .limit(drainedQueue.size());
        }

        public void add(T message) {
            this.messageQueue.add(new QueuedMessage<>(message, Instant.now().getLong(ChronoField.MILLI_OF_SECOND) + this.waitTime));
        }
    }

    static class DocumentUpdateBuffer extends AbstractMessageBuffer<MetadataDocumentMessage> {
        DocumentUpdateBuffer(MessageHandler messageHandler, long waitTime) {
            super(messageHandler, waitTime);
        }

        @Override
        public void flush(float flushPercent) {
            super.bufferedMessageStream().forEach(message -> super.messageHandler.handleMetadataDocumentUpdate(message.getMessage()));
        }

        @Override
        public void add(MetadataDocumentMessage message) {
            super.messageQueue.add(new QueuedMessage<>(message, message.getTimestamp() + super.waitTime));
        }
    }

    private void initFlusher() {
        scheduler.scheduleAtFixedRate(() -> this.documentUpdateBuffer.flush(100), 0, 5, TimeUnit.SECONDS);
    }

    @Data
    static class QueuedMessage<T> implements Delayed {

        private final long intendedStartTime;
        private final T message;

        public QueuedMessage(T message, long intendedStartTime) {
            this.intendedStartTime = intendedStartTime;
            this.message = message;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delay = intendedStartTime - System.currentTimeMillis();
            return unit.convert(delay, MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            long otherDelay = other.getDelay(MILLISECONDS);
            return Math.toIntExact(getDelay(TimeUnit.MILLISECONDS) - otherDelay);
        }
    }

}
