package org.humancellatlas.ingest;


import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.util.JwtService;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Ignore("this is an integration test for testing locally")
@SpringBootTest()
public class JwtTest {
    @Autowired
    JwtService jwtService;

    @Autowired
    IngestApiClient ingestApiClient;
    @Test
    public void testComponentInstantiation() throws Exception {
        Assertions.assertThat(jwtService).isNotNull();
        Condition<? super String> c;
        Assertions.assertThat(System.getenv()).containsKey("GOOGLE_APPLICATION_CREDENTIALS");
        Assertions.assertThat(jwtService.getToken()).isNotNull();
        ingestApiClient.verifyIngestConnection();
    }
}
