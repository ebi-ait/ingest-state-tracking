package org.humancellatlas.ingest.client;

import org.humancellatlas.ingest.messaging.MetadataDocumentMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Component
public class IngestApiClient {
    @Value("#{environment.INGEST_API_ROOT ?: 'http://api.ingest.dev.data.humancellatlas.org'}")
    private URI ingestApiRoot;

    private final RestTemplate restTemplate;

    private URI submissionEnvelopesLink;
    private Map<String, URI> metadataTypesLinkMap;

    public IngestApiClient() {
        // default constructor, used normally and allowing for environmental variable injection of INGEST_API_ROOT
        this.restTemplate = new RestTemplate();
    }

    public IngestApiClient(URI ingestApiRoot) {
        // alt constructor to allow overwiring of ingest API root
        this.ingestApiRoot = ingestApiRoot;
        this.restTemplate = new RestTemplate();
    }

    public void init() {

    }

    public SubmissionEnvelopeReference retrieveSubmissionEnvelopeReference(String submissionEnvelopeId) {
        return new SubmissionEnvelopeReference(
                submissionEnvelopeId,
                UUID.randomUUID(),
                URI.create(submissionEnvelopesLink.toString().concat(submissionEnvelopeId)));
    }

    public MetadataDocumentReference retrieveMetadataDocumentReference(String documentType, String metadataDocumentId) {
        return new MetadataDocumentReference(
                metadataDocumentId,
                UUID.randomUUID(),
                URI.create(metadataTypesLinkMap.get(documentType).toString().concat(metadataDocumentId)));
    }
}
