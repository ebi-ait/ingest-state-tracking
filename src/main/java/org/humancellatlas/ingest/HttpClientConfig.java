package org.humancellatlas.ingest;

import org.humancellatlas.ingest.client.AddJWTTokenHeaderInterceptor;
import org.humancellatlas.ingest.client.util.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Configuration
public class HttpClientConfig {

    @Autowired
    JwtService jwtService;

    @Autowired
    private Environment environment;
    @Bean
    @Profile("!test")
    public RestTemplate restTemplateWithAuth(@Autowired AddJWTTokenHeaderInterceptor addJWTTokenHeaderInterceptor) {

        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        restTemplate.setInterceptors(Collections.singletonList(addJWTTokenHeaderInterceptor));
        return restTemplate;
    }

    @Bean
    @Profile("test")
    public RestTemplate restTemplateNoAuth() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

}
