package org.humancellatlas.ingest.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class IngestClientHealthIndicator extends AbstractHealthIndicator {

    private final IngestApiClient ingestApiClient;

    @Autowired
    public IngestClientHealthIndicator(IngestApiClient ingestApiClient) {
        super("IngestApiClient health check failed");
        Assert.notNull(ingestApiClient, "IngestApiClient must not be null");
        this.ingestApiClient = ingestApiClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try {
            ingestApiClient.verifyIngestConnection();
            builder.up();
        } catch (Exception e) {
            builder.outOfService().withException(e);
        }
    }
}
