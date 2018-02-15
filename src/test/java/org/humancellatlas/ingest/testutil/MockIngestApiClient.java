package org.humancellatlas.ingest.testutil;

import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.client.IngestApiClient;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by rolando on 15/02/2018.
 */
public class MockIngestApiClient {
    public static final String INGEST_API_HOST = "localhost";
    public static final int INGEST_API_PORT = 8080;
    public static final URI INGEST_API_ROOT;
    public static final String INGEST_API_ROOT_STRING;

    static {
        try {
            INGEST_API_ROOT = new URIBuilder().setHost(INGEST_API_HOST).setPort(INGEST_API_PORT).setScheme("http").build();
            INGEST_API_ROOT_STRING = INGEST_API_ROOT.toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static IngestApiClient create() {
        return new IngestApiClient(INGEST_API_ROOT);

    }
}
