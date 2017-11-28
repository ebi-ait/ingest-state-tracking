package org.humancellatlas.ingest.messaging;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
public class Constants {
    public class Queues {
        public static final String DOCUMENT_CREATED = "ingest.metadata.created.queue";
        public static final String DOCUMENT_UPDATE = "ingest.metadata.update.queue";
        public static final String ENVELOPE_CREATED = "ingest.envelope.created.queue";
        public static final String ENVELOPE_UPDATE = "ingest.envelope.updated.queue";
    }
}
