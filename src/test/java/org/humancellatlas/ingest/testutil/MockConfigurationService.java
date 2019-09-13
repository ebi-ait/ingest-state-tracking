package org.humancellatlas.ingest.testutil;

import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.state.SubmissionState;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rolando on 15/02/2018.
 */
public class MockConfigurationService {
    public static final String INGEST_API_HOST = "localhost";
    public static final int INGEST_API_PORT = 8088;
    public static final URI INGEST_API_ROOT;
    public static final String INGEST_API_ROOT_STRING;
    public static final int UPDATER_PERIOD_MILLISECONDS = 5;

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
        configurationService.setUpdaterPeriodMs(UPDATER_PERIOD_MILLISECONDS);
        configurationService.setStateUpdateRels(mockStateUpdateRels());

        return configurationService;
    }

    public static Map<SubmissionState,String> mockStateUpdateRels() {
        Map<SubmissionState, String> stateUpdateRelMap = new HashMap<>();
        stateUpdateRelMap.put(SubmissionState.SUBMITTED, "commitSubmit");
        return stateUpdateRelMap;    }
}
