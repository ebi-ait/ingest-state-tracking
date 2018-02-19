package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.testutil.MockConfigurationService;
import org.junit.After;
import org.junit.Before;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.*;

import static org.humancellatlas.ingest.testutil.MockConfigurationService.INGEST_API_ROOT_STRING;
import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Created by rolando on 08/02/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class IngestApiClientTest {
    private IngestApiClient ingestApiClient;

    @Before
    public void before() {
        ingestApiClient = new IngestApiClient(MockConfigurationService.create());
        ingestApiClient.init();
    }

    @After
    public void after(){

    }

    public IngestApiClientTest(){ }

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

        stubFor(
                get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(metadataDocumentResponse))));

        stubFor(
                get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString() + "/submissionEnvelopes"))
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

        stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(submissionEnvelopeJson))));

        SubmissionEnvelope mockEnvelope = ingestApiClient.retrieveSubmissionEnvelope(submissionEnvelopeReference);

        assertNotNull(mockEnvelope.getSubmissionState());
        assertTrue(mockEnvelope.getSubmissionState().equals("Pending"));
    }

    @Test
    public void testUpdateSubmissionEnvelopeState() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        UUID mockEnvelopeUUID = UUID.randomUUID();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,
                new URI(mockEnvelopeCallbackLocation));

        class EnvelopePatchRequestJson {
            @JsonProperty("submissionState") String submissionState;

            EnvelopePatchRequestJson() {
                this.submissionState = SubmissionState.SUBMITTED.toString();
            }
        }

        EnvelopePatchRequestJson envelopePatchRequestJson = new EnvelopePatchRequestJson();

        stubFor(
                patch(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .withRequestBody(equalToJson(new ObjectMapper().writeValueAsString(envelopePatchRequestJson)))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopePatchRequestJson))));

        ingestApiClient.updateEnvelopeState(submissionEnvelopeReference, SubmissionState.SUBMITTED);

        verify(
                patchRequestedFor(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withRequestBody(equalToJson(new ObjectMapper().writeValueAsString(envelopePatchRequestJson))));

    }

}
