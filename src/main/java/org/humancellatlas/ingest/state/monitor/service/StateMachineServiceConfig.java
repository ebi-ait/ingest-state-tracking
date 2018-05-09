package org.humancellatlas.ingest.state.monitor.service;

import lombok.NonNull;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;
import org.springframework.statemachine.service.DefaultStateMachineService;
import org.springframework.statemachine.service.StateMachineService;

/**
 * Created by rolando on 09/05/2018.
 */
@Configuration
public class StateMachineServiceConfig {

    @Bean
    @Autowired
    public StateMachineService<SubmissionState, SubmissionEvent> stateMachineService(
            StateMachineFactory<SubmissionState, SubmissionEvent> stateMachineFactory,
            StateMachineRuntimePersister<SubmissionState, SubmissionEvent, String> stateMachineRuntimePersister) {
        return new DefaultStateMachineService<SubmissionState, SubmissionEvent>(stateMachineFactory, stateMachineRuntimePersister);
    }

}
