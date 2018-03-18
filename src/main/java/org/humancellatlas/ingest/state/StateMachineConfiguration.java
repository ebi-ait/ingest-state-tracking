package org.humancellatlas.ingest.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import java.util.Collections;
import java.util.Map;

import static org.humancellatlas.ingest.state.MetadataDocumentInfo.DOCUMENT_ID;
import static org.humancellatlas.ingest.state.MetadataDocumentInfo.DOCUMENT_STATE;
import static org.humancellatlas.ingest.state.SubmissionEvent.ALL_TASKS_COMPLETE;
import static org.humancellatlas.ingest.state.SubmissionEvent.CLEANUP_STARTED;
import static org.humancellatlas.ingest.state.SubmissionEvent.CONTENT_ADDED;
import static org.humancellatlas.ingest.state.SubmissionEvent.DOCUMENT_PROCESSED;
import static org.humancellatlas.ingest.state.SubmissionEvent.PROCESSING_FAILED;
import static org.humancellatlas.ingest.state.SubmissionEvent.PROCESSING_STARTED;
import static org.humancellatlas.ingest.state.SubmissionEvent.SUBMISSION_REQUESTED;
import static org.humancellatlas.ingest.state.SubmissionEvent.VALIDATION_STARTED;
import static org.humancellatlas.ingest.state.SubmissionState.CLEANUP;
import static org.humancellatlas.ingest.state.SubmissionState.COMPLETE;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENTS_INVALID;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENTS_VALID;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENTS_VALIDATING;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENTS_WAITING;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENT_VALIDATION;
import static org.humancellatlas.ingest.state.SubmissionState.DOCUMENT_VALIDATION_STARTING;
import static org.humancellatlas.ingest.state.SubmissionState.DRAFT;
import static org.humancellatlas.ingest.state.SubmissionState.INVALID;
import static org.humancellatlas.ingest.state.SubmissionState.PENDING;
import static org.humancellatlas.ingest.state.SubmissionState.PROCESSING;
import static org.humancellatlas.ingest.state.SubmissionState.SUBMITTED;
import static org.humancellatlas.ingest.state.SubmissionState.VALID;
import static org.humancellatlas.ingest.state.SubmissionState.VALIDATING;
import static org.humancellatlas.ingest.state.SubmissionState.VALIDATION_STATE_EVAL_JUNCTION;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Configuration
@EnableStateMachineFactory
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
                .state(PROCESSING)
                .state(CLEANUP)
                .end(COMPLETE);
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionState, SubmissionEvent> transitions) throws Exception{
        transitions
            .withExternal()
                .source(PENDING).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
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
            .withExternal()
                .source(VALID).target(SUBMITTED)
                .event(SUBMISSION_REQUESTED)
                .and()
            .withExternal()
                .source(SUBMITTED).target(PROCESSING)
                .event(PROCESSING_STARTED)
                .and()
            .withExternal()
                .source(PROCESSING).target(CLEANUP)
                .event(CLEANUP_STARTED)
                .and()
            .withExternal()
                .source(PROCESSING).target(SUBMITTED)
                .event(PROCESSING_FAILED)
                .and()
            .withExternal()
                .source(CLEANUP).target(COMPLETE)
                .event(ALL_TASKS_COMPLETE);

    }


    private Action<SubmissionState, SubmissionEvent> timerAction() {
        return context -> System.out.println("Validating...");
    }

    private Guard<SubmissionState, SubmissionEvent> documentsInvalidGuard() {
        return context -> {
            Map<Object, Object> docMap = Collections.synchronizedMap(context.getExtendedState().getVariables());

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                }
                else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    }
                    else {
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
            Map<Object, Object> docMap = Collections.synchronizedMap(context.getExtendedState().getVariables());

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                }
                else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    }
                    else {
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
            Map<Object, Object> docMap = Collections.synchronizedMap(context.getExtendedState().getVariables());

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
            if(! documentState.equals(MetadataDocumentState.VALID)) {
                context.getExtendedState().getVariables().put(documentId, documentState);
            } else {
                if ( context.getExtendedState().getVariables().containsKey(documentId)){
                    context.getExtendedState().getVariables().remove(documentId);
                }
            }
        };
    }
}
