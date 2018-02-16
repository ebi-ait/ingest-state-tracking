package org.humancellatlas.ingest.config;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Created by rolando on 15/02/2018.
 */
@Service("configuration")
public class ConfigurationService implements InitializingBean {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${INGEST_API_ROOT:'api.ingest.dev.data.humancellatlas.org'}")
    private String ingestApiRootString;
    @Value("${UPDATER_PERIOD_SECONDS:5}")
    private String updaterPeriodSecondsString;

    @Getter @Setter private URI ingestApiUri;
    @Getter @Setter private int updaterPeriodSeconds;

    private void init() {
        try {
            this.ingestApiUri = new URI(ingestApiRootString);
            this.updaterPeriodSeconds = Integer.parseInt(updaterPeriodSecondsString);
        } catch (Exception e) {
            log.error("Failed to intialize configuration vars", e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
