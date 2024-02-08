package org.humancellatlas.ingest;


import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.util.JwtCredentialsProperties;
import org.humancellatlas.ingest.client.util.JwtService;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest()
@Ignore("this is an integration test for testing locally")
public class JwtTest {
    @Autowired
    JwtService jwtService;
    @Autowired
    JwtCredentialsProperties jwtCredentialsProperties;
    @Autowired
    IngestApiClient ingestApiClient;
    @Ignore("this is an integration test for testing locally")
//    @Test
    public void testComponentInstantiation() throws Exception {
        assertThat(jwtService).isNotNull();
        Condition<? super String> c;
        assertThat(jwtCredentialsProperties.getPrivateKey()).isNotNull();
        assertThat(jwtCredentialsProperties.getPrivateKeyId()).isNotNull();
        assertThat(jwtCredentialsProperties.getClientEmail()).isNotNull();

//        assertThat(jwtService.getToken()).isNotNull();
//        ingestApiClient.verifyIngestConnection();
    }
}



