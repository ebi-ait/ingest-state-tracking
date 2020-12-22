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
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
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
                .state(VALIDATING)
                .state(VALID)
                .state(INVALID)
                .junction(VALIDATION_STATE_EVAL_JUNCTION)
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
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionState, SubmissionEvent> transitions) throws Exception {
        transitions
                .withExternal()
                .source(PENDING).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                /* draft, validating, valid or invalid? */
                .withExternal()
                .source(DRAFT).target(VALIDATION_STATE_EVAL_JUNCTION)
                .action(addOrUpdateContent())
                .event(DOCUMENT_PROCESSED)
                .and()

                .withExternal()
                .source(VALIDATING).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()

                .withExternal()
                .source(VALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()

                .withExternal()
                .source(INVALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()

                .withJunction()
                .source(VALIDATION_STATE_EVAL_JUNCTION)
                .first(INVALID, documentsInvalidGuard())
                .then(VALIDATING, documentsValidatingGuard())
                .then(VALID, allValidGuard())
                .last(DRAFT)
                .and()

                /* valid -> submitted */
                .withExternal()
                .source(VALID).target(SUBMITTED)
                .event(SUBMISSION_REQUESTED)
                .and()

                /* submitted -> draft */
                .withExternal()
                .source(SUBMITTED).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()

                /* submitted -> processing */
                .withExternal()
                .source(SUBMITTED).target(PROCESSING_STATE_EVAL_JUNCTION)
                .event(PROCESSING_STATE_UPDATE)
                .action(trackDocument(Constants.MANIFEST_TRACKER))
                .and()

                /* processing -> archiving */
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

                /* processing -> submitted */
                .withExternal()
                .source(PROCESSING).target(SUBMITTED)
                .event(PROCESSING_FAILED)
                .and()

                /* archiving -> archived */
                .withExternal()
                .source(ARCHIVING).target(ARCHIVED)
                .event(ARCHIVING_COMPLETE)
                .and()

                /* archived -> exporting */
                .withExternal()
                .source(ARCHIVED).target(EXPORTING_STATE_EVAL_JUNCTION)
                .event(EXPORTING_STATE_UPDATE)
                .action(trackDocument(Constants.EXPERIMENT_TRACKER))
                .and()

                /* submitted -> exporting */
                .withExternal()
                .source(SUBMITTED).target(EXPORTING_STATE_EVAL_JUNCTION)
                .event(EXPORTING_STATE_UPDATE)
                .action(trackDocument(Constants.EXPERIMENT_TRACKER))
                .and()

                /* exporting -> exported */
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

                /* exported -> draft */
                .withExternal()
                .source(EXPORTED).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()

                /* exported -> cleanup */
                .withExternal()
                .source(EXPORTED).target(CLEANUP)
                .event(CLEANUP_STARTED)
                .and()

                /* cleanup -> complete */
                .withExternal()
                .source(CLEANUP).target(COMPLETE)
                .event(ALL_TASKS_COMPLETE);
    }

    private Guard<SubmissionState, SubmissionEvent> documentsInvalidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                } else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    } else {
                        MetadataDocumentState documentState = (MetadataDocumentState) value;
                        log.debug(String.format("Testing content from extended state. Document tracker: { %s : %s }",
                                documentId,
                                documentState));
                        if (documentState.equals(MetadataDocumentState.INVALID)) {
                            return true;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return false;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> documentsValidatingGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                } else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    } else {
                        MetadataDocumentState documentState = (MetadataDocumentState) value;
                        log.debug(String.format("Testing content from extended state. Document tracker: { %s : %s }",
                                documentId,
                                documentState));
                        if (documentState.equals(MetadataDocumentState.VALIDATING)) {
                            return true;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return false;
        };
    }


    private Guard<SubmissionState, SubmissionEvent> allValidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            return docMap.entrySet().size() == 0;
        };
    }

    private Action<SubmissionState, SubmissionEvent> addOrUpdateContent() {
        return context -> {
            // retrieve the id of the document
            String documentId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);

            // retrieve the state of the document
            MetadataDocumentState documentState =
                    context.getMessageHeaders().get(DOCUMENT_STATE, MetadataDocumentState.class);

            // add the document and it's state to the extended context
            log.debug(String.format("Adding content to extended state. Document tracker: { %s : %s }",
                    documentId,
                    documentState));
            Map<String, MetadataDocumentState> metadataDocumentTracker = getMetadataDocumentTrackerFromContext(context);

            if (metadataDocumentTracker == null) {
                // add the metadata document state map
                metadataDocumentTracker = new ConcurrentHashMap<>();
                context.getExtendedState().getVariables().put(Constants.METADATA_DOCUMENT_TRACKER, metadataDocumentTracker);
            }

            if (!documentState.equals(MetadataDocumentState.VALID)) {
                metadataDocumentTracker.put(documentId, documentState);
            } else {
                if (metadataDocumentTracker.containsKey(documentId)) {
                    metadataDocumentTracker.remove(documentId);
                }
            }
        };
    }

    private Guard<SubmissionState, SubmissionEvent> stillProcessingGuard(String tracker) {
        return context -> {
            DocumentTracker documentTracker = getDocumentTrackerFromContext(context, tracker);
            return !documentTracker.allDocumentsCompleted();
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
            }

            if (targetState.equals(MetadataDocumentState.COMPLETE)
                    && documentTracker.getDocumentStateMap().get(processId).equals(MetadataDocumentState.PROCESSING)) {
                documentTracker.setComplete(processId);
            } else {
                documentTracker.setProcessing(processId);
            }
        };
    }

    private Map<String, MetadataDocumentState> getMetadataDocumentTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context) {
        return (Map<String, MetadataDocumentState>) context.getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
    }

    private DocumentTracker getDocumentTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context, String tracker) {
        return (DocumentTracker) context.getExtendedState().getVariables().get(tracker);
    }
}
