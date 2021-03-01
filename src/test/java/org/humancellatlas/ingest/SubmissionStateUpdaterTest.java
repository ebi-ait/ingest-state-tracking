package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateUpdater;
import org.humancellatlas.ingest.testutil.MockConfigurationService;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.humancellatlas.ingest.testutil.MockConfigurationService.INGEST_API_ROOT_STRING;
import static org.humancellatlas.ingest.testutil.MockConfigurationService.mockStateUpdateRels;
import static org.junit.Assert.*;


/**
 * Created by rolando on 15/02/2018.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SubmissionStateUpdaterTest {

    private IngestApiClient ingestApiClient;
    private ConfigurationService config;
    private SubmissionStateUpdater submissionStateUpdater;
    
    private WireMockServer wireMockServer;

    public SubmissionStateUpdaterTest() {
    }

    @BeforeEach
    public void before() {
        ingestApiClient = new IngestApiClient(MockConfigurationService.create());
        ingestApiClient.init();
        config = MockConfigurationService.create();
        submissionStateUpdater = new SubmissionStateUpdater(ingestApiClient, config);
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

    @AfterEach
    public void after() {
        submissionStateUpdater.stop();
    }

    @Test
    public void testOnlyKeepsOneCopyOfAnUpdateRequest() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        String mockEnvelopeUUID = UUID.randomUUID().toString();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID, SubmissionState.PENDING,
                new URI(mockEnvelopeCallbackLocation));

        submissionStateUpdater.requestStateUpdateForEnvelope(submissionEnvelopeReference, SubmissionState.SUBMITTED);
        submissionStateUpdater.requestStateUpdateForEnvelope(submissionEnvelopeReference, SubmissionState.VALID);

        assertTrue(submissionStateUpdater.getPendingUpdates().size() == 1);
        assertTrue(submissionStateUpdater.getPendingUpdates().iterator().next().getToState().equals(SubmissionState.VALID));

    }

    @Test
    public void testUpdateSubmissionState() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        String mockEnvelopeUUID = UUID.randomUUID().toString();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,SubmissionState.PENDING,
                new URI(mockEnvelopeCallbackLocation));

        class EnvelopeInitialJson {
            @JsonProperty("submissionState") String submissionState;
            @JsonProperty("_links") Map<String, Object> _links;

            EnvelopeInitialJson() {
                this.submissionState = SubmissionState.VALID.toString();
                _links = new HashMap<String, Object>() {{
                    put("self", new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + mockEnvelopeCallbackLocation);
                    }});
                    put(mockStateUpdateRels().get(SubmissionState.SUBMITTED), new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + mockEnvelopeCallbackLocation + "/mockCommitSubmit");
                    }});
                }};
            }
        }

        class EnvelopePatchRequestJson {
            @JsonProperty("submissionState") String submissionState;

            EnvelopePatchRequestJson() {
                this.submissionState = SubmissionState.SUBMITTED.toString();
            }
        }

        class EnvelopePatchedJson {
            @JsonProperty("submissionState") String submissionState;
            @JsonProperty("_links") Map<String, Object> _links;

            EnvelopePatchedJson() {
                this.submissionState = SubmissionState.SUBMITTED.toString();
                _links = new HashMap<String, Object>() {{
                    put("self", new HashMap<String, Object>() {{
                        put("href", INGEST_API_ROOT_STRING + mockEnvelopeCallbackLocation);
                    }});
                }};
            }
        }

        EnvelopeInitialJson envelopeInitialJson = new EnvelopeInitialJson();
        EnvelopePatchedJson envelopePatchedJson = new EnvelopePatchedJson();

        // scenario: envelope state is initially in state "valid", the updater should patch it and it should then be in state "submitted"
        wireMockServer.stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopeInitialJson))));

        wireMockServer.stubFor(
                put(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString() + "/mockCommitSubmit")).inScenario("update submission state")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopePatchedJson)))
                        .willSetStateTo("submission state transitioned"));

        wireMockServer.stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString())).inScenario("update submission state")
                        .whenScenarioStateIs("submission state transitioned")
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopePatchedJson))));


        assertEquals(
                ingestApiClient.retrieveSubmissionEnvelope(submissionEnvelopeReference).getSubmissionState().toUpperCase(),
                SubmissionState.VALID.toString().toUpperCase());

        submissionStateUpdater.requestStateUpdateForEnvelope(submissionEnvelopeReference, SubmissionState.SUBMITTED);
        assertTrue(submissionStateUpdater.getPendingUpdates().size() == 1);


        Thread.sleep((config.getUpdaterPeriodSeconds() * 1000) * 3); // wait for an update to happen, x 2 to be sure

        assertEquals(
                ingestApiClient.retrieveSubmissionEnvelope(submissionEnvelopeReference).getSubmissionState().toUpperCase(),
                SubmissionState.SUBMITTED.toString().toUpperCase());

        assertTrue(submissionStateUpdater.getPendingUpdates().size() == 0);

    }
}
