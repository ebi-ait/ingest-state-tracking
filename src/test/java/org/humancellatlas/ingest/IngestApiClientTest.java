package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.testutil.MockConfigurationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.humancellatlas.ingest.testutil.MockConfigurationService.INGEST_API_ROOT_STRING;
import static org.humancellatlas.ingest.testutil.MockConfigurationService.mockStateUpdateRels;
import static org.junit.Assert.*;

/**
 * Created by rolando on 08/02/2018.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class IngestApiClientTest {
    private IngestApiClient ingestApiClient;
    private WireMockServer wireMockServer;

    @BeforeEach
    public void before() {
        ingestApiClient = new IngestApiClient(MockConfigurationService.create());
        ingestApiClient.init();
    }
    
    @BeforeEach
    public void setupWireMockServer() {
        wireMockServer = new WireMockServer(8088);
        wireMockServer.start();
    }

    @AfterEach
    public void teardownWireMockServer() {
        wireMockServer.stop();
        wireMockServer.resetAll();
    }
    
    public IngestApiClientTest(){ }
    
    @Test
    public void testGetMetadataDocumentInfo() throws Exception {
        MetadataDocumentReference mockMetadataDocumentReference = new MetadataDocumentReference(
                "mock-id",
                UUID.randomUUID().toString(),
                new URI("/mockmetadatatype/1234"));

        String mockEnvelopeUUID = UUID.randomUUID().toString();

        class MetadataDocumentJson {
            @JsonProperty("validationState") String validationState;
            @JsonProperty("_links") Map<String, Object> _links;

            MetadataDocumentJson(){
                validationState = "Valid";
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

        class EnvelopeJson {
            @JsonProperty("uuid") Map<String, Object> uuid;
            @JsonProperty("_links")  Map<String, Object> _links;

            EnvelopeJson() {
                uuid = new HashMap<String, Object>() {{
                    put("uuid", mockEnvelopeUUID);
                }};
                _links = new HashMap<String, Object>() {{
                    put("self", new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + "/submissionEnvelopes/mock-envelope-id");
                    }});
                }};
            }
        }

        Object envelopeJson = new EnvelopeJson();

        class MetadataDocumentEmbeddedSubmissionEnvelopesJson {
            @JsonProperty("_embedded") Map<String, Object> _embedded;

            MetadataDocumentEmbeddedSubmissionEnvelopesJson() {
                _embedded = new HashMap<String, Object>() {{
                    put("submissionEnvelopes", Arrays.asList(envelopeJson));
                }};
            }
        }

        Object metadataDocumentResponse = new MetadataDocumentJson();
        Object metadataDocumentEmbeddedEnvelopesResponse = new MetadataDocumentEmbeddedSubmissionEnvelopesJson();

        wireMockServer.stubFor(
                get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(metadataDocumentResponse))));

        wireMockServer.stubFor(
                get(urlEqualTo(mockMetadataDocumentReference.getCallbackLocation().toString() + "/submissionEnvelopes"))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(metadataDocumentEmbeddedEnvelopesResponse))));

        wireMockServer.stubFor(
                get(urlEqualTo("/submissionEnvelopes/mock-envelope-id"))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/hal+json")
                                            .withBody(new ObjectMapper().writeValueAsString(envelopeJson))));

        MetadataDocument mockMetadataDocument = new MetadataDocument();
        mockMetadataDocument.setReferencedEnvelopes(ingestApiClient.envelopeReferencesFromEnvelopeIds(Collections.singletonList("mock-envelope-id")));

        assertTrue(mockMetadataDocument.getReferencedEnvelopes().size() == 1);
        assertTrue(mockMetadataDocument.getReferencedEnvelopes().get(0).getId().equals("mock-envelope-id"));
        assertTrue(mockMetadataDocument.getReferencedEnvelopes().get(0).getUuid().equals(mockEnvelopeUUID));
    }

    @Test
    public void testGetSubmissionEnvelopeInfo() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        String mockEnvelopeUUID = UUID.randomUUID().toString();
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

        wireMockServer.stubFor(
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
        String mockEnvelopeUUID = UUID.randomUUID().toString();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,
                new URI(mockEnvelopeCallbackLocation));

        class EnvelopeJson {
            @JsonProperty("submissionState") String submissionState;
            @JsonProperty("_links") Map<String, Object> _links;

            EnvelopeJson() {
                this.submissionState = SubmissionState.SUBMITTED.toString();
                _links = new HashMap<String, Object>() {{
                    put(mockStateUpdateRels().get(SubmissionState.SUBMITTED), new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + mockEnvelopeCallbackLocation + "/mockCommitSubmit");
                    }});
                }};
            }
        }

        class EnvelopeTransitionedJson {
            @JsonProperty("submissionState") String submissionState;

            EnvelopeTransitionedJson() {
                submissionState = SubmissionState.SUBMITTED.toString();
            }
        }

        Object envelopeJson = new EnvelopeJson();
        Object envelopeTransitioned = new EnvelopeTransitionedJson();

        wireMockServer.stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopeJson))));


        wireMockServer.stubFor(
                put(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString() + "/mockCommitSubmit"))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/hal+json")
                                            .withBody(new ObjectMapper().writeValueAsString(envelopeTransitioned))));


        ingestApiClient.updateEnvelopeState(submissionEnvelopeReference, SubmissionState.SUBMITTED);


        wireMockServer.verify(
                getRequestedFor(
                        urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString())));


        wireMockServer.verify(
                putRequestedFor(
                        urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString() + "/mockCommitSubmit" )));

    }

}
