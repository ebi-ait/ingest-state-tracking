package org.humancellatlas.ingest.client;

import lombok.Getter;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.messaging.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 28/11/17
 */
@Component
public class IngestApiClient  implements InitializingBean {
    @Value("${INGEST_API_ROOT:'http://api.ingest.dev.data.humancellatlas.org'}")
    private String ingestApiRootString;
    private URI ingestApiRoot;

    @Getter
    private final RestTemplate restTemplate;

    private String submissionEnvelopesPath;
    private Map<String, String> metadataTypesLinkMap = new HashMap<>();

    private final Logger log = LoggerFactory.getLogger(getClass());

    public IngestApiClient() {
        // default constructor, used normally and allowing for environmental variable injection of INGEST_API_ROOT
        this.restTemplate = new RestTemplate();
    }

    public IngestApiClient(URI ingestApiRoot) {
        // alt constructor to allow overwiring of ingest API root
        this.ingestApiRoot = ingestApiRoot;
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
    }

    public void init() {
        this.submissionEnvelopesPath = "/submissionEnvelopes";
        this.metadataTypesLinkMap.put("samples", ingestApiRootString + "/samples");
    }

    public SubmissionEnvelope updateEnvelopeState(SubmissionEnvelopeReference envelopeReference, SubmissionState submissionState) {
        String envelopeURIString = this.ingestApiRoot.toString() + envelopeReference.getCallbackLocation();
        URI envelopeURI = uriFor(envelopeURIString);

        try {
            return this.restTemplate.patchForObject(
                    envelopeURI,
                    halRequestEntityFor(new SubmissionEnvelope(submissionState.toString().toUpperCase())),
                    SubmissionEnvelope.class);
        } catch (HttpClientErrorException e) {
            log.trace("Failed to patch the state of a submission envelope with ID %s and callback link %s. Status code %s", envelopeReference.getId(), envelopeReference.getCallbackLocation(), Integer.toString(e.getRawStatusCode()));
            throw e;
        }

    }

    public SubmissionEnvelope retrieveSubmissionEnvelope(SubmissionEnvelopeReference envelopeReference) {
        String envelopeURIString = this.ingestApiRoot.toString() + envelopeReference.getCallbackLocation();

        URI envelopeURI = uriFor(envelopeURIString);
        Traverson halTraverser = halTraverserOn(envelopeURI);

        String submissionState = halTraverser.follow("self").toObject("$.submissionState");
        return new SubmissionEnvelope(submissionState);
    }

    public MetadataDocument retrieveMetadataDocument(MetadataDocumentReference documentReference) {
        String documentURIString = this.ingestApiRoot.toString() + documentReference.getCallbackLocation().toString();

        URI documentURI = uriFor(documentURIString);
        Traverson halTraverser = halTraverserOn(documentURI);

        String validationState = halTraverser.follow("self").toObject("$.documentState");
        List<String> relatedSubmissionIds = halTraverser.follow("submissionEnvelopes")
                .toObject(new ParameterizedTypeReference<PagedResources<Resource<LinkedHashMap>>>() {} )
                .getContent()
                .stream().map(resource -> extractIdFromSubmissionEnvelopeURI(resource.getLink("self").getHref()))
                .collect(Collectors.toList());

        return new MetadataDocument(validationState, relatedSubmissionIds);
    }

    public SubmissionEnvelopeReference referenceForSubmissionEnvelope(String submissionEnvelopeId) {
        return new SubmissionEnvelopeReference(
                submissionEnvelopeId,
                UUID.randomUUID(),
                URI.create(submissionEnvelopesPath.concat(submissionEnvelopeId)));
    }

    public SubmissionEnvelopeReference referenceForSubmissionEnvelope(SubmissionEnvelopeMessage message) {
        return referenceForSubmissionEnvelope(message.getDocumentId());
    }

    public MetadataDocumentReference referenceForMetadataDocument(String documentType, String metadataDocumentId) {
        return new MetadataDocumentReference(
                metadataDocumentId,
                UUID.randomUUID(),
                URI.create(metadataTypesLinkMap.get(documentType).toString().concat(metadataDocumentId)));
    }

    public MetadataDocumentReference referenceForMetadataDocument(MetadataDocumentMessage message) {
        return referenceForMetadataDocument(message.getDocumentType(), message.getDocumentId());
    }

    private Traverson halTraverserOn(URI baseUri) {
        return new Traverson(baseUri, MediaTypes.HAL_JSON);
    }

    private String extractIdFromSubmissionEnvelopeURI(String envelopeURI) {
        return extractIdFromSubmissionEnvelopeURI(uriFor(envelopeURI));
    }

    private URI uriFor(String uriString) {
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            log.trace(String.format("Error trying to create URI from string %s", uriString));
            throw new RuntimeException(e);
        }
    }

    private String extractIdFromSubmissionEnvelopeURI(URI envelopeURI) {
        String envelopeURIPath = envelopeURI.getPath();
        return envelopeURIPath.substring(envelopeURIPath.lastIndexOf('/') + 1);
    }

    private <T> HttpEntity<T> halRequestEntityFor(T entity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaTypes.HAL_JSON));
        return new HttpEntity<>(entity, headers);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
