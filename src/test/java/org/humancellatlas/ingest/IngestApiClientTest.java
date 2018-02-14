package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.junit.After;
import org.junit.Before;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.*;

import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Created by rolando on 08/02/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class IngestApiClientTest {

    private String INGEST_API_HOST;
    private int INGEST_API_PORT;
    private URI INGEST_API_ROOT;
    private String INGEST_API_ROOT_STRING;

    private IngestApiClient ingestApiClient;

    @Before
    public void before() throws Exception {
        ingestApiClient = new IngestApiClient(this.INGEST_API_ROOT);
    }

    @After
    public void after(){

    }

    public IngestApiClientTest() throws Exception {
        this.INGEST_API_HOST = "localhost";
        this.INGEST_API_PORT = 8080;
        this.INGEST_API_ROOT = new URIBuilder().setHost(INGEST_API_HOST).setPort(8080).setScheme("http").build();
        this.INGEST_API_ROOT_STRING = this.INGEST_API_ROOT.toString();
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);

    @Test
    public void testGetMetadataDocumentInfo() throws Exception {
        MetadataDocumentReference mockMetadataDocumentReference = new MetadataDocumentReference(
                "mock-id",
                UUID.randomUUID(),
                new URI("/mockmetadatatype/1234"));

        String mockEnvelopeUUID = UUID.randomUUID().toString();

        class MetadataDocumentJson {
            @JsonProperty("documentState") String documentState;
            @JsonProperty("_links") Map<String, Object> _links;

            MetadataDocumentJson(){
                documentState = "Valid";
                _links = new HashMap<String, Object>() {{
                    put("self", new HashMap<String, Object>() {{
                        put("href",  INGEST_API_ROOT_STRING + mockMetadataDocumentReference.getCallbackLocation());
                    }});
                    put("submissionEnvelopes", new HashMap<String, Object>(){{
                        put("href", INGEST_API_ROOT_STRING + mockMetadataDocumentReference.getCallbackLocation() + "/submissionEnvelopes");
                    }});
                }};
            }
        }

        class MetadataDocumentEmbeddedSubmissionEnvelopesJson {
            @JsonProperty("_embedded") Map<String, Object> _embedded;

            MetadataDocumentEmbeddedSubmissionEnvelopesJson() {
                _embedded = new HashMap<String, Object>() {{
                    put("submissionEnvelopes", Arrays.asList(
                            new HashMap<String, Object>() {{
                                put("uuid",  new HashMap<String, Object>() {{
                                   put("uuid", mockEnvelopeUUID);
                                }});
                                put("_links", new HashMap<String, Object>() {{
                                    put("self", new HashMap<String, Object>() {{
                                        put("href", INGEST_API_ROOT_STRING + "/submissionEnvelopes/mock-envelope-id" );
                                    }});
                                }});
                            }}
                    ));
                }};
            }
        }

        Object metadataDocumentResponse = new MetadataDocumentJson();
        Object metadataDocumentEmbeddedEnvelopesResponse = new MetadataDocumentEmbeddedSubmissionEnvelopesJson();

        stubFor(get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString()))
                .withHeader("Accept", equalTo("application/hal+json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/hal+json")
                        .withBody(new ObjectMapper().writeValueAsString(metadataDocumentResponse))));

        stubFor(get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString() + "/submissionEnvelopes"))
                .withHeader("Accept", equalTo("application/hal+json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/hal+json")
                        .withBody(new ObjectMapper().writeValueAsString(metadataDocumentEmbeddedEnvelopesResponse))));

        MetadataDocument mockMetadataDocument = ingestApiClient.retrieveMetadataDocument(mockMetadataDocumentReference);

        assertNotNull(mockMetadataDocument.getValidationState());
        assertTrue(mockMetadataDocument.getSubmissionIds().size() == 1);
        assertTrue(mockMetadataDocument.getSubmissionIds().get(0).equals("mock-envelope-id"));
    }

    @Test
    public void testGetSubmissionEnvelopeInfo() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        UUID mockEnvelopeUUID = UUID.randomUUID();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,
                new URI(mockEnvelopeCallbackLocation));

        class SubmissionEnvelopeJson {
            @JsonProperty("submissionState") String submissionState;
            @JsonProperty("_links") Map<String, Object> _links;

            SubmissionEnvelopeJson() {
                submissionState = "Pending";
                _links = new HashMap<String, Object>() {{
                    put("self", new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + "/submissionEnvelopes/mock-envelope-id");
                    }});
                }};
            }
        }

        SubmissionEnvelopeJson submissionEnvelopeJson = new SubmissionEnvelopeJson();

        stubFor(get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                .withHeader("Accept", equalTo("application/hal+json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/hal+json")
                        .withBody(new ObjectMapper().writeValueAsString(submissionEnvelopeJson))));

        SubmissionEnvelope mockEnvelope = ingestApiClient.retrieveSubmissionEnvelope(submissionEnvelopeReference);

        assertNotNull(mockEnvelope.getSubmissionState());
        assertTrue(mockEnvelope.getSubmissionState().equals("Pending"));
    }

}
