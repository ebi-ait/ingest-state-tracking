package org.humancellatlas.ingest.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;

/**
 * Created by rolando on 11/03/2018.
 */
@Configuration
public class QueueConfig implements RabbitListenerConfigurer  {

    @Bean
    TopicExchange stateTrackingExchange() { return new TopicExchange(Constants.Exchanges.STATE_TRACKING); }

    @Bean
    TopicExchange assayExchange() { return new TopicExchange(Constants.Exchanges.BUNDLE_EXCHANGE); }

    /* queues */

    @Bean
    Queue queueEnvelopeCreate() { return new Queue(Constants.Queues.ENVELOPE_CREATED, false); }

    @Bean
    Queue queueEnvelopeStateUpdate() { return new Queue(Constants.Queues.ENVELOPE_UPDATE, false); }

    @Bean
    Queue queueManifestSubmitted() { return new Queue(Constants.Queues.MANIFEST_SUBMITTED, false); }

    @Bean
    Queue queueManifestCompleted() { return new Queue(Constants.Queues.MANIFEST_COMPLETED, false); }

    @Bean
    Queue queueExperimentSubmitted() { return new Queue(Constants.Queues.EXPERIMENT_SUBMITTED, false); }

    @Bean
    Queue queueExperimentCompleted() { return new Queue(Constants.Queues.EXPERIMENT_EXPORTED, false); }

    /* queue bindings */

    @Bean
    Binding bindingEnvelopeCreate(Queue queueEnvelopeCreate, TopicExchange stateTrackingExchange) {
        return BindingBuilder.bind(queueEnvelopeCreate).to(stateTrackingExchange).with(Constants.RoutingKeys.ENVELOPE_CREATE);
    }

    @Bean
    Binding bindiEnvelopestateUpdate(Queue queueEnvelopeStateUpdate, TopicExchange stateTrackingExchange) {
        return BindingBuilder.bind(queueEnvelopeStateUpdate).to(stateTrackingExchange).with(Constants.RoutingKeys.ENVELOPE_STATE_UPDATE);
    }

    @Bean
    Binding bindingBundleableProcessSubmitted(Queue queueManifestSubmitted, TopicExchange assayExchange) {
        return BindingBuilder.bind(queueManifestSubmitted).to(assayExchange).with(Constants.RoutingKeys.MANIFEST_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    Binding bindingBundleableProcessCompleted(Queue queueManifestCompleted, TopicExchange assayExchange) {
        return BindingBuilder.bind(queueManifestCompleted).to(assayExchange).with(Constants.RoutingKeys.MANIFEST_COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding bindingExperimentSubmitted(Queue queueExperimentSubmitted, TopicExchange assayExchange) {
        return BindingBuilder.bind(queueExperimentSubmitted).to(assayExchange).with(Constants.RoutingKeys.EXPERIMENT_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    Binding bindingExperimentExported(Queue queueExperimentCompleted, TopicExchange assayExchange) {
        return BindingBuilder.bind(queueExperimentCompleted).to(assayExchange).with(Constants.RoutingKeys.EXPERIMENT_EXPORTED_ROUTING_KEY);
    }

    /* rabbit message config */

    @Bean
    public MessageConverter messageConverter() {
        return jackson2Converter();
    }

    @Bean
    public MappingJackson2MessageConverter jackson2Converter() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        return new MappingJackson2MessageConverter();
    }

    @Bean
    public DefaultMessageHandlerMethodFactory myHandlerMethodFactory() {
        DefaultMessageHandlerMethodFactory factory = new DefaultMessageHandlerMethodFactory();
        factory.setMessageConverter(jackson2Converter());
        return factory;
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitMessagingTemplate rabbitMessagingTemplate(RabbitTemplate rabbitTemplate) {
        RabbitMessagingTemplate rmt = new RabbitMessagingTemplate(rabbitTemplate);
        rmt.setMessageConverter(this.jackson2Converter());
        return rmt;
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.setMessageHandlerMethodFactory(myHandlerMethodFactory());
    }

}

