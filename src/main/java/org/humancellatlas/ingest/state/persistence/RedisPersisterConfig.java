package org.humancellatlas.ingest.state.persistence;

import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.data.redis.RedisPersistingStateMachineInterceptor;
import org.springframework.statemachine.data.redis.RedisStateMachineContextRepository;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.statemachine.data.redis.RedisStateMachineRepository;
import org.springframework.statemachine.persist.RepositoryStateMachinePersist;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

/**
 * Created by rolando on 02/05/2018.
 */
@Configuration
public class RedisPersisterConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public StateMachinePersist<SubmissionState, SubmissionEvent, String> stateMachinePersist(RedisConnectionFactory connectionFactory) {
        RedisStateMachineContextRepository<SubmissionState, SubmissionEvent> repository =
                new RedisStateMachineContextRepository<SubmissionState, SubmissionEvent>(connectionFactory);
        return new RepositoryStateMachinePersist<SubmissionState, SubmissionEvent>(repository);
    }

    @Bean
    public RedisStateMachinePersister<SubmissionState, SubmissionEvent> redisStateMachinePersister(
            StateMachinePersist<SubmissionState, SubmissionEvent, String> stateMachinePersist) {
        return new RedisStateMachinePersister<SubmissionState, SubmissionEvent>(stateMachinePersist);
    }
}
