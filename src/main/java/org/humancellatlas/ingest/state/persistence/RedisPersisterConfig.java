package org.humancellatlas.ingest.state.persistence;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.config.ConfigurationService;
import org.humancellatlas.ingest.state.SubmissionEvent;
import org.humancellatlas.ingest.state.SubmissionState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.statemachine.data.redis.RedisStateMachineContextRepository;
import org.springframework.statemachine.data.redis.RedisStateMachinePersister;
import org.springframework.statemachine.persist.RepositoryStateMachinePersist;


/**
 * Created by rolando on 02/05/2018.
 */
@Configuration
@DependsOn("configuration")
@AllArgsConstructor
public class RedisPersisterConfig {
    private final @NonNull ConfigurationService config;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(config.getRedisHost(), config.getRedisPort());
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
