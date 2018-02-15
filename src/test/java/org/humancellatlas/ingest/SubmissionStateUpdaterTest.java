package org.humancellatlas.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.humancellatlas.ingest.client.IngestApiClient;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.exception.CoreStateUpdatedFailedException;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateUpdater;
import org.humancellatlas.ingest.testutil.MockIngestApiClient;
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
import java.util.UUID;
import static org.junit.Assert.*;


/**
 * Created by rolando on 15/02/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SubmissionStateUpdaterTest {

    private IngestApiClient ingestApiClient;
    private SubmissionStateUpdater submissionStateUpdater;

    public SubmissionStateUpdaterTest() {
    }

    @Before
    public void before() {
        ingestApiClient = MockIngestApiClient.create();
        submissionStateUpdater = new SubmissionStateUpdater(ingestApiClient);
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8080);


    @Test
    public void testUpdateSubmissionState() throws Exception {
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


        try {
            submissionStateUpdater.update(submissionEnvelopeReference, SubmissionState.SUBMITTED);
        } catch (CoreStateUpdatedFailedException e) {
            fail();
        }

    }

    @Test
    public void testUpdateSubmissionState_NonExistentSubmission() throws Exception {
        // exception path (non existent envelope)
        String mockEnvelopeId = "mock-envelope-id-does-not-exist";
        UUID mockEnvelopeUUID = UUID.randomUUID();
        String mockEnvelopeCallbackLocation = "/submissionEnvelopes/" + mockEnvelopeId;

        SubmissionEnvelopeReference submissionEnvelopeReference = new SubmissionEnvelopeReference(
                mockEnvelopeId,
                mockEnvelopeUUID,
                new URI(mockEnvelopeCallbackLocation));

        stubFor(
                patch(urlEqualTo(submissionEnvelopeReference.getCallbackLocation().toString()))
                        .withHeader("Accept", equalTo("application/hal+json"))
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/hal+json")
                                .withBody("")));

        try {
            submissionStateUpdater.update(submissionEnvelopeReference, SubmissionState.SUBMITTED);
            fail();
        } catch (CoreStateUpdatedFailedException e) {
            assertTrue(true);
        }
    }
}
