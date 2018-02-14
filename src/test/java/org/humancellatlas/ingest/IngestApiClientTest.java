package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.junit.After;
import org.junit.Before;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import wiremock.net.minidev.json.JSONArray;
import wiremock.net.minidev.json.JSONObject;

import java.net.URI;
import java.util.*;

/**
 * Created by rolando on 08/02/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class IngestApiClientTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);
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

    @Test
    public void testGetSubmissionIdsForMetadataDocument() throws Exception {

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
        assert mockMetadataDocument.getSubmissionIds().size() == 1;
    }


}
