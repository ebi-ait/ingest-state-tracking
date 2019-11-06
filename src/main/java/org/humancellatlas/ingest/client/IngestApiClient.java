package org.humancellatlas.ingest.client;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.client.util.EnvelopeReferenceCache;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.model.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.client.Traverson;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


import java.io.IOException;
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
@DependsOn("configuration")
public class IngestApiClient implements InitializingBean {
    private ConfigurationService config;
    
    @Getter
    private RestTemplate restTemplate;

    private String submissionEnvelopesPath;
    private Map<String, String> metadataTypesLinkMap = new HashMap<>();
    private EnvelopeReferenceCache envelopeReferenceCache;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public IngestApiClient() {}
    
    @Autowired
    public IngestApiClient(@Autowired ConfigurationService config) {
        this.config = config;
    }

    public void init() {
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        this.submissionEnvelopesPath = "/submissionEnvelopes";
        this.metadataTypesLinkMap.put("sample",  config.getIngestApiUri() + "/samples");
        this.envelopeReferenceCache = new EnvelopeReferenceCache(100);
    }

    public SubmissionEnvelope updateEnvelopeState(SubmissionEnvelopeReference envelopeReference, SubmissionState submissionState) {
        String envelopeURIString = config.getIngestApiUri().toString() + envelopeReference.getCallbackLocation();
        URI envelopeURI = uriFor(envelopeURIString);

        try {
            // get the link for the state update API
            String stateUpdateUri = halTraverserOn(envelopeURI).follow(config.getStateUpdateRels().get(submissionState))
                                                               .asLink()
                                                               .getHref();
            return this.restTemplate.exchange(stateUpdateUri,
                                              HttpMethod.PUT,
                                              halRequestEntityFor(Collections.emptyMap()),
                                              SubmissionEnvelope.class)
                                    .getBody();
        } catch (HttpClientErrorException e) {
            log.trace("Failed to patch the state of a submission envelope with ID %s and callback link %s. Status code %s", envelopeReference.getId(), envelopeReference.getCallbackLocation(), Integer.toString(e.getRawStatusCode()));
            throw e;
        }
    }

    public SubmissionEnvelope retrieveSubmissionEnvelope(SubmissionEnvelopeReference envelopeReference) {
        String envelopeURIString = config.getIngestApiUri().toString() + envelopeReference.getCallbackLocation();

        URI envelopeURI = uriFor(envelopeURIString);
        JsonNode documentJson = getRestTemplate().exchange(envelopeURI,
                                                           HttpMethod.GET,
                                                           halRequestEntityFor(Collections.emptyMap()),
                                                           JsonNode.class)
                                                 .getBody();

        String submissionState = documentJson.at(JsonPointer.valueOf("/submissionState")).asText();
        return new SubmissionEnvelope(submissionState);
    }

    public SubmissionEnvelopeReference envelopeReferencesFromEnvelopeId(String envelopeId){
        return this.envelopeReferenceFromEnvelopeId(envelopeId);
    }

    public SubmissionEnvelopeReference referenceForSubmissionEnvelope(SubmissionEnvelopeMessage message) {
        return new SubmissionEnvelopeReference(message.getDocumentId(),
                                               message.getDocumentUuid(),
                                               URI.create(message.getCallbackLink()));
    }

    public SubmissionEnvelopeReference referenceForSubmissionEnvelope(UUID envelopeUuid) {
        URI findSubmissionByUuid = uriFor(halTraverserOn(config.getIngestApiUri()).follow("submissionEnvelopes")
                                                                                  .follow("search")
                                                                                  .follow("findByUuid").asLink().getHref());
        URI submissionByUuid = UriComponentsBuilder.fromUri(findSubmissionByUuid)
                                                   .queryParam("uuid", envelopeUuid.toString())
                                                   .build().toUri();

        JsonNode envelopeJson = this.restTemplate.getForEntity(submissionByUuid, JsonNode.class)
                                                 .getBody();

        URI envelopeUri = URI.create(envelopeJson.at(JsonPointer.valueOf("/_links/self/href")).asText());

        return new SubmissionEnvelopeReference(extractIdFromSubmissionEnvelopeURI(envelopeUri),
                                               envelopeUuid.toString(),
                                               extractCallbackUriFromSubmissionEnvelopeUri(envelopeUri));
    }

    public MetadataDocumentReference referenceForMetadataDocument(MetadataDocumentMessage message) {
        return new MetadataDocumentReference(message.getDocumentId(),
                                             message.getDocumentUuid(),
                                             URI.create(message.getCallbackLink()));
    }

    private Traverson halTraverserOn(URI baseUri) {
        return new Traverson(baseUri, MediaTypes.HAL_JSON);
    }


    /**
     * first looks in a cache of envelope IDs to envelope references, retrieves envelope info from the core API
     * when there's a cache miss
     *
     * @param envelopeId
     * @return
     */
    private SubmissionEnvelopeReference envelopeReferenceFromEnvelopeId(String envelopeId) {
        Optional<SubmissionEnvelopeReference> envelopeReferenceOptional = Optional.ofNullable(envelopeReferenceCache.get(envelopeId));
        if(envelopeReferenceOptional.isPresent()) {
            return envelopeReferenceOptional.get();
        } else {
            SubmissionEnvelopeReference envelopeReference = envelopeReferenceFromEnvelopeUri(URI.create(config.getIngestApiUri().toString() + submissionEnvelopesPath + "/" + envelopeId));
            envelopeReferenceCache.put(envelopeId, envelopeReference);
            return envelopeReference;
        }
    }

    private SubmissionEnvelopeReference envelopeReferenceFromEnvelopeUri(URI envelopeUri) {
        JsonNode envelopeJson = halTraverserOn(envelopeUri).follow("self")
                                                           .toObject(JsonNode.class);
        String envelopeUuid = envelopeJson.at(JsonPointer.valueOf("/uuid/uuid")).asText();
        String envelopeId = extractIdFromSubmissionEnvelopeURI(envelopeUri);
        URI envelopeCallbackLocation = extractCallbackUriFromSubmissionEnvelopeUri(envelopeUri);

        return new SubmissionEnvelopeReference(envelopeId, envelopeUuid, envelopeCallbackLocation);
    }

    private URI uriFor(String uriString) {
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            log.trace(String.format("Error trying to listenerFor URI from string %s", uriString));
            throw new RuntimeException(e);
        }
    }

    private String extractIdFromSubmissionEnvelopeURI(URI envelopeURI) {
        String envelopeURIPath = envelopeURI.getPath();
        return envelopeURIPath.substring(envelopeURIPath.lastIndexOf('/') + 1);
    }

    private URI extractCallbackUriFromSubmissionEnvelopeUri(URI envelopeUri) {
        return URI.create(envelopeUri.getPath());
    }

    private <T> HttpEntity<String> halRequestEntityFor(T entity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaTypes.HAL_JSON));
        headers.setContentType(MediaTypes.HAL_JSON);
        try {
            return new HttpEntity<>(new ObjectMapper().writeValueAsString(entity), headers);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
