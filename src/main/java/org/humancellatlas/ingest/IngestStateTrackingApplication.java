package org.humancellatlas.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@SpringBootApplication
@PropertySources({
        @PropertySource("classpath:application.properties")
})
public class IngestStateTrackingApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestStateTrackingApplication.class, args);
    }
}
