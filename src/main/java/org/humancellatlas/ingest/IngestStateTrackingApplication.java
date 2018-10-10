package org.humancellatlas.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class IngestStateTrackingApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestStateTrackingApplication.class, args);
    }
}
