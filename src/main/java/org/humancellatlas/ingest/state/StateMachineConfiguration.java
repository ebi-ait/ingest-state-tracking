package org.humancellatlas.ingest.state;

import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import static org.humancellatlas.ingest.state.SubmissionStates.*;
import static org.humancellatlas.ingest.state.SubmissionEvents.*;

import java.util.EnumSet;

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
                .end(COMPLETE)
                .states(EnumSet.allOf(SubmissionStates.class));
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionStates, SubmissionEvents> transitions) throws Exception {
        transitions
                .withExternal().source(PENDING).target(DRAFT)
                .event(CONTENT_ADDED)
                .and()
                .withExternal().source(DRAFT).target(VALIDATING)
                .event(VALIDATION_STARTED)
                .and()
                .withExternal().source(VALIDATING).target(VALID)
                .event(ALL_DOCUMENTS_ARE_VALID)
                .and()
                .withExternal().source(VALIDATING).target(INVALID)
                .event(DOCUMENTS_ARE_INVALID)
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
}
