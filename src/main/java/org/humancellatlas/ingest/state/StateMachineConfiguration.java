package org.humancellatlas.ingest.state;

import lombok.AllArgsConstructor;
import org.humancellatlas.ingest.messaging.Constants;
import org.humancellatlas.ingest.state.monitor.util.DocumentTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.humancellatlas.ingest.state.MetadataDocumentInfo.*;
import static org.humancellatlas.ingest.state.SubmissionEvent.*;
import static org.humancellatlas.ingest.state.SubmissionState.*;

/**
 * State machine configuration for handling submission process states.
 */
@Configuration
@EnableStateMachineFactory
@AllArgsConstructor
public class StateMachineConfiguration extends EnumStateMachineConfigurerAdapter<SubmissionState, SubmissionEvent> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void configure(StateMachineStateConfigurer<SubmissionState, SubmissionEvent> states) throws Exception {
        states.withStates()
                .initial(PENDING)
                .state(DRAFT)
                .state(METADATA_VALIDATING)
                .state(METADATA_VALID)
                .state(METADATA_INVALID)
                .junction(VALIDATION_STATE_EVAL_JUNCTION)
                .state(GRAPH_VALIDATION_REQUESTED)
                .state(GRAPH_VALIDATING)
                .state(GRAPH_VALID)
                .state(GRAPH_INVALID)
                .state(SUBMITTED)
                .junction(PROCESSING_STATE_EVAL_JUNCTION)
                .state(PROCESSING)
                .state(ARCHIVING)
                .state(ARCHIVED)
                .junction(EXPORTING_STATE_EVAL_JUNCTION)
                .state(EXPORTING)
                .state(EXPORTED)
                .state(CLEANUP)
                .end(COMPLETE);

        log.info("State machine states configured.");
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<SubmissionState, SubmissionEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(PENDING).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(DRAFT).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(METADATA_VALIDATING).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(METADATA_VALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(METADATA_INVALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(DRAFT).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withExternal()
                .source(METADATA_VALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withExternal()
                .source(METADATA_INVALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withJunction()
                .source(VALIDATION_STATE_EVAL_JUNCTION)
                .first(METADATA_INVALID, documentsInvalidGuard())
                .then(METADATA_VALIDATING, documentsValidatingGuard())
                .then(METADATA_VALID, allValidGuard())
                .last(DRAFT)
                .and()
                .withExternal()
                .source(GRAPH_VALID).target(METADATA_VALID)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withExternal()
                .source(GRAPH_INVALID).target(METADATA_VALID)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withExternal()
                .source(EXPORTED).target(METADATA_VALID)
                .event(DOCUMENT_DELETED)
                .action(removeDocument())
                .and()
                .withExternal()
                .source(METADATA_VALID).target(GRAPH_VALIDATION_REQUESTED)
                .event(GRAPH_VALIDATION_STARTED)
                .and()
                .withExternal()
                .source(GRAPH_INVALID).target(GRAPH_VALIDATION_REQUESTED)
                .event(GRAPH_VALIDATION_STARTED)
                .and()
                .withExternal()
                .source(GRAPH_VALIDATION_REQUESTED).target(GRAPH_VALIDATING)
                .event(GRAPH_VALIDATION_PROCESSING)
                .and()
                .withExternal()
                .source(GRAPH_VALIDATING).target(GRAPH_VALID)
                .event(GRAPH_VALIDATION_COMPLETE)
                .and()
                .withExternal()
                .source(GRAPH_VALIDATING).target(GRAPH_INVALID)
                .event(GRAPH_VALIDATION_INVALID)
                .and()
                .withExternal()
                .source(GRAPH_VALID).target(SUBMITTED)
                .event(SUBMISSION_REQUESTED)
                .action(resetTracker(Constants.EXPERIMENT_TRACKER))
                .and()
                .withExternal()
                .source(GRAPH_VALIDATION_REQUESTED).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(GRAPH_VALID).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(GRAPH_INVALID).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(GRAPH_VALIDATING).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(SUBMITTED).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(SUBMITTED).target(PROCESSING_STATE_EVAL_JUNCTION)
                .event(PROCESSING_STATE_UPDATE)
                .action(trackDocument(Constants.MANIFEST_TRACKER))
                .and()
                .withExternal()
                .source(PROCESSING).target(PROCESSING_STATE_EVAL_JUNCTION)
                .event(PROCESSING_STATE_UPDATE)
                .action(trackDocument(Constants.MANIFEST_TRACKER))
                .and()
                .withJunction()
                .source(PROCESSING_STATE_EVAL_JUNCTION)
                .first(PROCESSING, stillProcessingGuard(Constants.MANIFEST_TRACKER))
                .last(ARCHIVING)
                .and()
                .withExternal()
                .source(PROCESSING).target(SUBMITTED)
                .event(PROCESSING_FAILED)
                .and()
                .withExternal()
                .source(ARCHIVING).target(ARCHIVED)
                .event(ARCHIVING_COMPLETE)
                .and()
                .withExternal()
                .source(ARCHIVED).target(EXPORTING_STATE_EVAL_JUNCTION)
                .event(EXPORTING_STATE_UPDATE)
                .action(trackDocument(Constants.EXPERIMENT_TRACKER))
                .and()
                .withExternal()
                .source(SUBMITTED).target(EXPORTING_STATE_EVAL_JUNCTION)
                .event(EXPORTING_STATE_UPDATE)
                .action(trackDocument(Constants.EXPERIMENT_TRACKER))
                .and()
                .withExternal()
                .source(EXPORTING).target(EXPORTING_STATE_EVAL_JUNCTION)
                .event(EXPORTING_STATE_UPDATE)
                .action(trackDocument(Constants.EXPERIMENT_TRACKER))
                .and()
                .withJunction()
                .source(EXPORTING_STATE_EVAL_JUNCTION)
                .first(EXPORTING, stillProcessingGuard(Constants.EXPERIMENT_TRACKER))
                .last(EXPORTED)
                .and()
                .withExternal()
                .source(EXPORTED).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal()
                .source(EXPORTED).target(CLEANUP)
                .event(CLEANUP_STARTED)
                .and()
                .withExternal()
                .source(CLEANUP).target(COMPLETE)
                .event(ALL_TASKS_COMPLETE);

        log.info("State machine transitions configured.");
    }

    private Action<SubmissionState, SubmissionEvent> addOrUpdateContent() {
        return context -> {
            String documentId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);
            MetadataDocumentState documentState = context.getMessageHeaders().get(DOCUMENT_STATE, MetadataDocumentState.class);

            log.debug("Action: addOrUpdateContent - Document ID: {}, Document State: {}", documentId, documentState);

            Map<String, MetadataDocumentState> metadataDocumentTracker = getMetadataDocumentTrackerFromContext(context);

            if (metadataDocumentTracker == null) {
                metadataDocumentTracker = new ConcurrentHashMap<>();
                context.getExtendedState().getVariables().put(Constants.METADATA_DOCUMENT_TRACKER, metadataDocumentTracker);
            }

            if (!documentState.equals(MetadataDocumentState.VALID)) {
                metadataDocumentTracker.put(documentId, documentState);
                log.info("Document ID: {} added to tracker with state: {}", documentId, documentState);
            } else {
                if (metadataDocumentTracker.containsKey(documentId)) {
                    metadataDocumentTracker.remove(documentId);
                    log.info("Document ID: {} removed from tracker", documentId);
                }
            }
        };
    }

    private Action<SubmissionState, SubmissionEvent> removeDocument() {
        return context -> {
            String documentId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);
            Map<String, MetadataDocumentState> metadataDocumentTracker = getMetadataDocumentTrackerFromContext(context);

            if (metadataDocumentTracker != null && metadataDocumentTracker.containsKey(documentId)) {
                metadataDocumentTracker.remove(documentId);
                log.info("Document ID: {} removed from tracker", documentId);
            } else {
                log.warn("Attempted to remove document ID: {} that does not exist in tracker", documentId);
            }
        };
    }

    private Action<SubmissionState, SubmissionEvent> resetTracker(String tracker) {
        return context -> {
            context.getExtendedState().getVariables().remove(tracker);
            log.info("Tracker {} reset", tracker);
        };
    }

    private Action<SubmissionState, SubmissionEvent> trackDocument(String tracker) {
        return context -> {
            String processId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);
            MetadataDocumentState targetState = context.getMessageHeaders().get(DOCUMENT_STATE, MetadataDocumentState.class);

            DocumentTracker documentTracker = (DocumentTracker) context.getExtendedState().getVariables().get(tracker);

            if (documentTracker == null) {
                int documentCount = context.getMessageHeaders().get(EXPECTED_DOCUMENT_COUNT, Integer.class);
                documentTracker = new DocumentTracker(documentCount);
                context.getExtendedState().getVariables().put(tracker, documentTracker);
                log.info("Created new DocumentTracker with expected document count: {}", documentCount);
            }

            if (targetState.equals(MetadataDocumentState.COMPLETE)
                    && documentTracker.getDocumentStateMap().get(processId).equals(MetadataDocumentState.PROCESSING)) {
                documentTracker.setComplete(processId);
                log.info("Document ID: {} marked as COMPLETE in tracker", processId);
            } else {
                documentTracker.setProcessing(processId);
                log.info("Document ID: {} marked as PROCESSING in tracker", processId);
            }
        };
    }

    private Guard<SubmissionState, SubmissionEvent> documentsInvalidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));
            for (Map.Entry<String, MetadataDocumentState> entry : docMap.entrySet()) {
                String documentId = entry.getKey();
                MetadataDocumentState documentState = entry.getValue();
                log.debug("Testing content from extended state. Document tracker: { {} : {} }", documentId, documentState);
                if (documentState.equals(MetadataDocumentState.INVALID)) {
                    log.info("Guard: documentsInvalidGuard - Document ID: {} is INVALID", documentId);
                    return true;
                }
            }
            log.info("Guard: documentsInvalidGuard - No documents are INVALID");
            return false;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> documentsValidatingGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));
            for (Map.Entry<String, MetadataDocumentState> entry : docMap.entrySet()) {
                String documentId = entry.getKey();
                MetadataDocumentState documentState = entry.getValue();
                log.debug("Testing content from extended state. Document tracker: { {} : {} }", documentId, documentState);
                if (documentState.equals(MetadataDocumentState.VALIDATING)) {
                    log.info("Guard: documentsValidatingGuard - Document ID: {} is VALIDATING", documentId);
                    return true;
                }
            }
            log.info("Guard: documentsValidatingGuard - No documents are VALIDATING");
            return false;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> allValidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));
            boolean allValid = docMap.isEmpty();
            log.info("Guard: allValidGuard - All documents valid: {}", allValid);
            return allValid;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> stillProcessingGuard(String tracker) {
        return context -> {
            DocumentTracker documentTracker = getDocumentTrackerFromContext(context, tracker);
            boolean stillProcessing = !documentTracker.allDocumentsCompleted();
            log.info("Guard: stillProcessingGuard - Tracker: {} - Still processing: {}", tracker, stillProcessing);
            return stillProcessing;
        };
    }

    private Map<String, MetadataDocumentState> getMetadataDocumentTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context) {
        return (Map<String, MetadataDocumentState>) context.getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
    }

    private DocumentTracker getDocumentTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context, String tracker) {
        return (DocumentTracker) context.getExtendedState().getVariables().get(tracker);
    }
}
