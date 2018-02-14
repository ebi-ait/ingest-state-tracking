package org.humancellatlas.ingest.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Created by rolando on 05/02/2018.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SubmissionEnvelope {
    private String submissionState;
}
