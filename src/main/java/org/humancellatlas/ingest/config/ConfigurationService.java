package org.humancellatlas.ingest.config;

import lombok.Getter;
import lombok.Setter;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rolando on 15/02/2018.
 */
@Service("configuration")
public class ConfigurationService implements InitializingBean {
    private final Logger log = LoggerFactory.getLogger(getClass());


    @Value("${INGEST_API_ROOT}")
    @Getter @Setter private URI ingestApiUri;
    @Value("${UPDATER_PERIOD_SECONDS:2}")
    @Getter @Setter private int updaterPeriodSeconds;
    @Value("${REDIS_HOST:localhost}")
    @Getter @Setter private String redisHost;
    @Value("${REDIS_PORT:6379}")
    @Getter @Setter private int redisPort;
    @Value("${NUM_HANDLER_THREADS:15}")
    @Getter @Setter private int numHandlerThreads;
    @Getter @Setter private Map<SubmissionState, String> stateUpdateRels;

    private void init() {
        try {

            // map of submissions states to the rels of the links for transitioning to that state
            this.stateUpdateRels = stateUpdateRelsMap();

        } catch (Exception e) {
            log.error("Failed to initialize configuration vars", e);
        }
    }

    private Map<SubmissionState, String> stateUpdateRelsMap() {
        // map of submissions states to the rels of the links for transitioning to that state
        Map<SubmissionState, String> stateUpdateRelMap = new HashMap<>();

        stateUpdateRelMap.put(SubmissionState.DRAFT, "commitDraft");
        stateUpdateRelMap.put(SubmissionState.METADATA_VALIDATING, "commitValidating");
        stateUpdateRelMap.put(SubmissionState.METADATA_INVALID, "commitInvalid");
        stateUpdateRelMap.put(SubmissionState.METADATA_VALID, "commitValid");
        stateUpdateRelMap.put(SubmissionState.GRAPH_VALIDATION_REQUESTED, "commitGraphValidationRequested");
        stateUpdateRelMap.put(SubmissionState.GRAPH_VALIDATING, "commitGraphValidating");
        stateUpdateRelMap.put(SubmissionState.GRAPH_VALID, "commitGraphValid");
        stateUpdateRelMap.put(SubmissionState.GRAPH_INVALID, "commitGraphInvalid");
        stateUpdateRelMap.put(SubmissionState.SUBMITTED, "commitSubmit");
        stateUpdateRelMap.put(SubmissionState.PROCESSING, "commitProcessing");
        stateUpdateRelMap.put(SubmissionState.ARCHIVING, "commitArchiving");
        stateUpdateRelMap.put(SubmissionState.ARCHIVED, "commitArchived");
        stateUpdateRelMap.put(SubmissionState.EXPORTING, "commitExporting");
        stateUpdateRelMap.put(SubmissionState.EXPORTED, "commitExported");
        stateUpdateRelMap.put(SubmissionState.CLEANUP, "commitCleanup");
        stateUpdateRelMap.put(SubmissionState.COMPLETE, "commitComplete");

        return stateUpdateRelMap;
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
