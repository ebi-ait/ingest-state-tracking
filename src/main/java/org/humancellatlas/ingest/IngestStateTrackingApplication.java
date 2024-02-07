package org.humancellatlas.ingest;

import org.humancellatlas.ingest.client.util.JwtCredentialsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@SpringBootApplication
@EnableScheduling
@PropertySources({
        @PropertySource("classpath:application.properties")
})
@EnableConfigurationProperties(JwtCredentialsProperties.class)
public class IngestStateTrackingApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestStateTrackingApplication.class, args);
    }
}
