package org.humancellatlas.ingest.messaging;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
public class Constants {
    public class Queues {
        public static final String MANIFEST_SUBMITTED = "ingest.state-tracking.bundle.submitted.queue";
        public static final String MANIFEST_COMPLETED = "ingest.state-tracking.bundle.completed.queue";

        public static final String EXPERIMENT_SUBMITTED = "ingest.terra.experiments.submitted.queue";
        public static final String EXPERIMENT_EXPORTED = "ingest.terra.experiments.exported.queue";

        public static final String ENVELOPE_CREATED = "ingest.state-tracking.envelope.created.queue";
        public static final String ENVELOPE_UPDATE = "ingest.state-tracking.envelope.updated.queue";
    }

    public class Exchanges {
        public static final String STATE_TRACKING = "ingest.state-tracking.exchange";
        public static final String BUNDLE_EXCHANGE = "ingest.bundle.exchange";
    }

    public class RoutingKeys {
        public static final String ENVELOPE_STATE_UPDATE = "ingest.state-tracking.envelope.state.update";
        public static final String ENVELOPE_CREATE = "ingest.state-tracking.envelope.create";
        public static final String METADATA_UPDATE = "ingest.state-tracking.document.update";
        public static final String MANIFEST_SUBMITTED_ROUTING_KEY = "ingest.bundle.*.submitted";
        public static final String MANIFEST_COMPLETED_ROUTING_KEY = "ingest.bundle.*.completed";

        public static final String EXPERIMENT_SUBMITTED_ROUTING_KEY = "ingest.terra.experiments.submitted";
        public static final String EXPERIMENT_EXPORTED_ROUTING_KEY = "ingest.terra.experiments.exported";

    }

    public static final String METADATA_DOCUMENT_TRACKER = "METADATA_DOCUMENT_TRACKER";
    public static final String MANIFEST_TRACKER = "MANIFEST_TRACKER";
    public static final String EXPERIMENT_TRACKER = "EXPERIMENT_TRACKER";


}
