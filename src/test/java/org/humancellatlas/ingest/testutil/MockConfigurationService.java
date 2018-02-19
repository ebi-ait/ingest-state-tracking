package org.humancellatlas.ingest.testutil;

import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.config.ConfigurationService;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by rolando on 15/02/2018.
 */
public class MockConfigurationService {
    public static final String INGEST_API_HOST = "localhost";
    public static final int INGEST_API_PORT = 8080;
    public static final URI INGEST_API_ROOT;
    public static final String INGEST_API_ROOT_STRING;
    public static final int UPDATER_PERIOD_SECONDS = 5;

    static {
        try {
            INGEST_API_ROOT = new URIBuilder().setHost(INGEST_API_HOST).setPort(INGEST_API_PORT).setScheme("http").build();
            INGEST_API_ROOT_STRING = INGEST_API_ROOT.toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static ConfigurationService create() {
        ConfigurationService configurationService = new ConfigurationService();
        configurationService.setIngestApiUri(INGEST_API_ROOT);
        configurationService.setUpdaterPeriodSeconds(UPDATER_PERIOD_SECONDS);

        return configurationService;
    }
}
