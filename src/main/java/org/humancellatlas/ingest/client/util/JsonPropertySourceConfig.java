package org.humancellatlas.ingest.client.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration
@PropertySource(
        value = "${google.application.credentials}",
        ignoreResourceNotFound = false,
        factory = JsonPropertySourceFactory.class,
        name = "gcp_credentials")
public class JsonPropertySourceConfig {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
