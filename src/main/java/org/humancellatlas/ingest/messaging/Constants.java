package org.humancellatlas.ingest.messaging;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
public class Constants {
    public class Queues {
        public static final String DOCUMENT_CREATED = "ingest.state-tracking.metadata.created.queue";
        public static final String DOCUMENT_UPDATE = "ingest.state-tracking.metadata.update.queue";
        public static final String ENVELOPE_CREATED = "ingest.state-tracking.envelope.created.queue";
        public static final String ENVELOPE_UPDATE = "ingest.state-tracking.envelope.updated.queue";
    }

    public class Exchanges {
        public static final String STATE_TRACKING = "ingest.state-tracking.exchange";
    }

    public class RoutingKeys {
        public static final String ENVELOPE_STATE_UPDATE = "ingest.state-tracking.envelope.state.update";
        public static final String ENVELOPE_CREATE = "ingest.state-tracking.envelope.create";
        public static final String METADATA_UPDATE = "ingest.state-tracking.document.update";
    }
}
