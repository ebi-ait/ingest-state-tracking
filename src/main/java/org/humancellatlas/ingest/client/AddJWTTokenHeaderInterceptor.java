package org.humancellatlas.ingest.client;

import org.humancellatlas.ingest.client.util.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AddJWTTokenHeaderInterceptor implements ClientHttpRequestInterceptor {
    @Autowired
    JwtService jwtService;
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        String token = null;
        try {
            token = jwtService.getToken();
        } catch (Exception e) {
            throw new RuntimeException("cannot get token: " + e.getMessage(), e);
        }
        headers.add("Authorization", "Bearer " + token);

        return execution.execute(request, body);
    }
}
