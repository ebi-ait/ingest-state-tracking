package org.humancellatlas.ingest.http;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.humancellatlas.ingest.messaging.Constants;
import org.humancellatlas.ingest.state.MetadataDocumentState;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Created by rolando on 05/09/2018.
 */
@Component
@Controller
@AllArgsConstructor
@Getter
public class MachineReportController {
    private final @NonNull SubmissionStateMonitor monitor;

    @RequestMapping(path = "machine-reports/{envelope_uuid}", method = RequestMethod.GET)
    ResponseEntity<?> addBiomaterialToEnvelope(@PathVariable("envelope_uuid") String envelopeUuidString) {
        UUID envelopeUuid = UUID.fromString(envelopeUuidString);
        Optional<StateMachine<SubmissionState, SubmissionEvent>> maybeSmForEnvelope = getMonitor().findStateMachine(envelopeUuid);
        if(maybeSmForEnvelope.isPresent()) {
            StateMachine<SubmissionState, SubmissionEvent> smForEnvelope = maybeSmForEnvelope.get();
            Map<String, MetadataDocumentState> documentsStatesMap = (Map<String, MetadataDocumentState>) smForEnvelope.getExtendedState()
                                                                                                                      .getVariables()
                                                                                                                      .get(Constants.METADATA_DOCUMENT_TRACKER);

            return ResponseEntity.ok(documentsStatesMap);
        } else {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND,
                                               String.format("No state machine found for envelope with uuid %s", envelopeUuidString));
        }

    }
}
