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
                .state(DRAFT, DOCUMENT_PROCESSED)
                .state(VALIDATING, CONTENT_ADDED)
                .state(VALID, DOCUMENT_PROCESSED)
                .state(INVALID, DOCUMENT_PROCESSED)
                .state(SUBMITTED)
                .state(PROCESSING)
                .state(CLEANUP)
                .end(COMPLETE)
                .and().withStates()
                .parent(VALIDATING)
                .initial(DOCUMENTS_WAITING)
                .entry(DOCUMENT_VALIDATION_STARTING)
                .state(DOCUMENTS_VALIDATING)
                .choice(DOCUMENT_VALIDATION)
                .exit(DOCUMENTS_VALID)
                .exit(DOCUMENTS_INVALID);
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionState, SubmissionEvent> transitions)
            throws Exception {
        transitions
                .withExternal().source(PENDING).target(DRAFT)
                .event(CONTENT_ADDED)
                .action(addOrUpdateContent())
                .and()
                .withExternal().source(DRAFT).target(VALIDATING)
                .event(VALIDATION_STARTED)
                .and()
                .withExternal().source(DOCUMENTS_WAITING).target(DOCUMENT_VALIDATION_STARTING)
                .and()
                .withEntry().source(DOCUMENT_VALIDATION_STARTING).target(DOCUMENTS_VALIDATING)
                .and()
                .withInternal().source(DOCUMENTS_VALIDATING).action(timerAction()).timer(1000)
                .and()
                .withExternal().source(DOCUMENTS_VALIDATING).target(DOCUMENTS_VALIDATING)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
                .withExternal().source(DOCUMENTS_VALIDATING).target(DOCUMENT_VALIDATION).guard(allValidatedGuard())
                .and()
                .withChoice().source(DOCUMENT_VALIDATION)
                .first(DOCUMENTS_VALID, allValidGuard()).last(DOCUMENTS_INVALID)
                .and()
                .withExit().source(DOCUMENTS_VALID).target(VALID)
                .and()
                .withExit().source(DOCUMENTS_INVALID).target(INVALID)
                .and()
                .withExternal().source(VALID).target(DRAFT)
                .event(CONTENT_ADDED)
                .and()
                .withExternal().source(INVALID).target(DRAFT)
                .event(CONTENT_ADDED)
                .and()
                .withExternal().source(VALID).target(SUBMITTED)
                .event(SUBMISSION_REQUESTED)
                .and()
                .withExternal().source(SUBMITTED).target(PROCESSING)
                .event(PROCESSING_STARTED)
                .and()
                .withExternal().source(PROCESSING).target(CLEANUP)
                .event(CLEANUP_STARTED)
                .and()
                .withExternal().source(PROCESSING).target(SUBMITTED)
                .event(PROCESSING_FAILED)
                .and()
                .withExternal().source(CLEANUP).target(COMPLETE)
                .event(ALL_TASKS_COMPLETE);
    }

    private Action<SubmissionState, SubmissionEvent> timerAction() {
        return context -> System.out.println("Validating...");
    }

    private Guard<SubmissionState, SubmissionEvent> allValidatedGuard() {
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
                        if (documentState.equals(MetadataDocumentState.DRAFT)) {
                            return false;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return true;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> allValidGuard() {
        return context -> {
            Map<Object, Object> docMap = Collections.synchronizedMap(context.getExtendedState().getVariables());

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                }
                else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                    }
                    else {
                        MetadataDocumentState documentState = (MetadataDocumentState) value;
                        log.debug(String.format("Testing content from extended state. Document tracker: { %s : %s }",
                                                documentId,
                                                documentState));
                        if (documentState.equals(MetadataDocumentState.INVALID)) {
                            return false;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return true;
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
            context.getExtendedState().getVariables().put(documentId, documentState);
        };
    }
}
