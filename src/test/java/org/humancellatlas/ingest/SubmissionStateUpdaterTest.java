package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateUpdater;
import org.humancellatlas.ingest.testutil.MockConfigurationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.humancellatlas.ingest.testutil.MockConfigurationService.INGEST_API_ROOT_STRING;
import static org.junit.Assert.*;


/**
 * Created by rolando on 15/02/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SubmissionStateUpdaterTest {

    private IngestApiClient ingestApiClient;
    private ConfigurationService config;
    private SubmissionStateUpdater submissionStateUpdater;

    public SubmissionStateUpdaterTest() {
    }

    @Before
    public void before() {
        ingestApiClient = new IngestApiClient(MockConfigurationService.create());
        ingestApiClient.init();
        config = MockConfigurationService.create();
        submissionStateUpdater = new SubmissionStateUpdater(ingestApiClient, config);
    }

    @After
    public void after() {
        submissionStateUpdater.stop();
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8088);


    @Test
    public void testOnlyKeepsOneCopyOfAnUpdateRequest() throws Exception {
        String mockEnvelopeId = "mock-envelope-id";
        String mockEnvelopeUUID = UUID.randomUUID().toString();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,
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
                mockEnvelopeUUID,
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
        EnvelopePatchRequestJson envelopePatchRequestJson = new EnvelopePatchRequestJson();
        EnvelopePatchedJson envelopePatchedJson = new EnvelopePatchedJson();

        // scenario: envelope state is initially in state "valid", the updater should patch it and it should then be in state "submitted"
        stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopeInitialJson))));

        stubFor(
                patch(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString())).inScenario("update submission state")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .withRequestBody(equalToJson(new ObjectMapper().writeValueAsString(envelopePatchRequestJson)))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody(new ObjectMapper().writeValueAsString(envelopePatchRequestJson)))
                        .willSetStateTo("submission state patched"));

        stubFor(
                get(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString())).inScenario("update submission state")
                        .whenScenarioStateIs("submission state patched")
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


        Thread.sleep((config.getUpdaterPeriodSeconds() * 1000) * 2); // wait for an update to happen, x 2 to be sure

        assertEquals(
                ingestApiClient.retrieveSubmissionEnvelope(submissionEnvelopeReference).getSubmissionState().toUpperCase(),
                SubmissionState.SUBMITTED.toString().toUpperCase());

        assertTrue(submissionStateUpdater.getPendingUpdates().size() == 0);

    }
}
