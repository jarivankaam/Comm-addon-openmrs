package com.azaricomm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Configuration
public class RabbitConfig {

    @Value("${app.rabbitmq.exchange:azaricomm.events}")
    private String exchangeName;

    @Value("${app.rabbitmq.input-queue:azaricomm.notifworker-input}")
    private String inputQueueName;

    @Value("${app.rabbitmq.output-queue:azaricomm.validated-notifications}")
    private String outputQueueName;

    // Input queue - where messages arrive from scheduler
    @Bean
    public Queue inputQueue() {
        return new Queue(inputQueueName, true, false, false);
    }

    // Output queue - where validated messages are published
    @Bean
    public Queue outputQueue() {
        return new Queue(outputQueueName, true, false, false);
    }

    // Topic exchange (already exists in scheduler, we're binding to it)
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // Binding for input queue to receive all notification messages
    @Bean
    public Binding inputBinding(Queue inputQueue, TopicExchange exchange) {
        return BindingBuilder.bind(inputQueue)
                .to(exchange)
                .with("notification.#");
    }

    // Binding for output queue (for communication-service to consume from)
    @Bean
    public Binding outputBinding(Queue outputQueue, TopicExchange exchange) {
        return BindingBuilder.bind(outputQueue)
                .to(exchange)
                .with("notification.validated");
    }

    // Jackson2JsonMessageConverter with support for Java 8+ types
    @Bean
    public Jackson2JsonMessageConverter jackson2MessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2MessageConverter());
        return rabbitTemplate;
    }
}
