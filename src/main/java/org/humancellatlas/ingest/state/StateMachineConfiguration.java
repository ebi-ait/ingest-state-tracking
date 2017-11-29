package org.humancellatlas.ingest.state;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import static org.humancellatlas.ingest.state.MessageHeaders.DOCUMENT_STATE;
import static org.humancellatlas.ingest.state.SubmissionEvents.ALL_DOCUMENTS_ARE_VALID;
import static org.humancellatlas.ingest.state.SubmissionEvents.ALL_TASKS_COMPLETE;
import static org.humancellatlas.ingest.state.SubmissionEvents.CLEANUP_STARTED;
import static org.humancellatlas.ingest.state.SubmissionEvents.CONTENT_ADDED;
import static org.humancellatlas.ingest.state.SubmissionEvents.DOCUMENTS_ARE_INVALID;
import static org.humancellatlas.ingest.state.SubmissionEvents.PROCESSING_FAILED;
import static org.humancellatlas.ingest.state.SubmissionEvents.PROCESSING_STARTED;
import static org.humancellatlas.ingest.state.SubmissionEvents.SUBMISSION_REQUESTED;
import static org.humancellatlas.ingest.state.SubmissionEvents.VALIDATION_STARTED;
import static org.humancellatlas.ingest.state.SubmissionStates.CLEANUP;
import static org.humancellatlas.ingest.state.SubmissionStates.COMPLETE;
import static org.humancellatlas.ingest.state.SubmissionStates.DOCUMENTS_WAITING;
import static org.humancellatlas.ingest.state.SubmissionStates.DOCUMENTS_INVALID;
import static org.humancellatlas.ingest.state.SubmissionStates.DOCUMENTS_VALID;
import static org.humancellatlas.ingest.state.SubmissionStates.DOCUMENTS_VALIDATING;
import static org.humancellatlas.ingest.state.SubmissionStates.DOCUMENT_VALIDATION;
import static org.humancellatlas.ingest.state.SubmissionStates.DRAFT;
import static org.humancellatlas.ingest.state.SubmissionStates.INVALID;
import static org.humancellatlas.ingest.state.SubmissionStates.PENDING;
import static org.humancellatlas.ingest.state.SubmissionStates.PROCESSING;
import static org.humancellatlas.ingest.state.SubmissionStates.SUBMITTED;
import static org.humancellatlas.ingest.state.SubmissionStates.VALID;
import static org.humancellatlas.ingest.state.SubmissionStates.VALIDATING;
import static org.humancellatlas.ingest.state.MessageHeaders.DOCUMENT_ID;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Configuration
@EnableStateMachineFactory
public class StateMachineConfiguration extends EnumStateMachineConfigurerAdapter<SubmissionStates, SubmissionEvents> {
    @Override
    public void configure(StateMachineStateConfigurer<SubmissionStates, SubmissionEvents> states) throws Exception {
        states.withStates()
                .initial(PENDING)
                .state(DRAFT)
                .state(VALIDATING)
                .state(VALID)
                .state(INVALID)
                .state(SUBMITTED)
                .state(PROCESSING)
                .end(COMPLETE)
                .and().withStates()
                    .parent(VALIDATING)
                    .initial(DOCUMENTS_WAITING)
                    .entry(DOCUMENTS_VALIDATING)
                    .choice(DOCUMENT_VALIDATION)
                    .exit(DOCUMENTS_VALID)
                    .exit(DOCUMENTS_INVALID);
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionStates, SubmissionEvents> transitions)
            throws Exception {
        transitions
                .withExternal().source(PENDING).target(DRAFT)
                .event(CONTENT_ADDED)
                .and()
                .withExternal().source(DRAFT).target(VALIDATING)
                .event(VALIDATION_STARTED)
                .and()
                .withExternal().source(DOCUMENTS_WAITING).target(DOCUMENTS_VALIDATING)
                .event(VALIDATION_STARTED)
                .and()
                .withEntry().source(DOCUMENTS_VALIDATING).target(DOCUMENT_VALIDATION)
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

    private Guard<SubmissionStates, SubmissionEvents> allValidGuard() {
        // check if all documents attached to the current state engine extended context are valid

        return null;
    }

    private Action<SubmissionStates, SubmissionEvents> addContent() {
        return context -> {
            // retrieve the id of the document
            String documentId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);

            // retrieve the state of the document
            DocumentStates documentState = context.getMessageHeaders().get(DOCUMENT_STATE, DocumentStates.class);

            // add the document and it's state to the extended context

        };
    }
}
