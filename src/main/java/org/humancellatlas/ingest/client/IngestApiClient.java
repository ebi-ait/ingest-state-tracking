package org.humancellatlas.ingest.client;

import lombok.Getter;
import org.apache.http.client.utils.URIBuilder;
import org.humancellatlas.ingest.client.model.MetadataDocument;
import org.humancellatlas.ingest.client.model.SubmissionEnvelope;
import org.humancellatlas.ingest.messaging.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.SubmissionEnvelopeMessage;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.client.Traverson;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

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
        this.restTemplate = new RestTemplate();
    }

    public void init() {
        this.submissionEnvelopesPath = "/submissionEnvelopes";

        this.metadataTypesLinkMap.put("samples", ingestApiRootString + "/samples");
    }

    public SubmissionEnvelope retrieveSubmissionEnvelope(SubmissionEnvelopeReference envelopeReference) {
        return this.restTemplate
                .getForEntity(ingestApiRoot.toString() + envelopeReference.getCallbackLocation(), SubmissionEnvelope.class)
                .getBody();
    }

    public MetadataDocument retrieveMetadataDocument(MetadataDocumentReference documentReference) {
        String documentURIString = this.ingestApiRoot.toString() + documentReference.getCallbackLocation().toString();

        try{
            URI documentURI = new URI(documentURIString);
            Traverson halTraverser = halTraverserOn(documentURI);

            String validationState = halTraverser.follow("self").toObject("$.documentState");
            List<String> relatedSubmissionIds = halTraverser.follow("submissionEnvelopes")
                    .toObject(new ParameterizedTypeReference<PagedResources<Resource<LinkedHashMap>>>() {} )
                    .getContent()
                    .stream().map(resource -> extractIdFromSubmissionEnvelopeURI(resource.getLink("self").getHref()))
                    .collect(Collectors.toList());

            return new MetadataDocument(validationState, relatedSubmissionIds);
        } catch (URISyntaxException e) {
            log.trace(String.format("Error trying to create URI from string %s", documentURIString));
            throw new RuntimeException(e);
        }

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
        try {
            return extractIdFromSubmissionEnvelopeURI(new URI(envelopeURI));
        } catch (URISyntaxException e) {
            log.trace("Received an invalid submission envelope URI string", e);
            throw new RuntimeException(e);
        }
    }

    private String extractIdFromSubmissionEnvelopeURI(URI envelopeURI) {
        String envelopeURIPath = envelopeURI.getPath();
        return envelopeURIPath.substring(envelopeURIPath.lastIndexOf('/') + 1);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
