package com.azaricomm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${rabbitmq.exchange:azaricomm.events}")
    private String exchangeName;

    @Value("${rabbitmq.queue:azaricomm.notifications}")
    private String queueName;

    @Value("${rabbitmq.routing-key:notification.#}")
    private String routingKey;

    private static final String RETRY_EXCHANGE = "azaricomm.retry";
    private static final int RETRY_DELAY_MS = 30_000;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // Main queue — failed messages go to the retry exchange
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", RETRY_EXCHANGE)
                .build();
    }

    @Bean
    public Binding binding(Queue notificationQueue, TopicExchange exchange) {
        return BindingBuilder.bind(notificationQueue).to(exchange).with(routingKey);
    }

    // DLX that receives rejected messages from the main queue
    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    // Wait queue: messages sit here for RETRY_DELAY_MS, then return to the main exchange
    @Bean
    public Queue retryWaitQueue() {
        return QueueBuilder.durable(queueName + ".wait")
                .withArgument("x-message-ttl", RETRY_DELAY_MS)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", "notification.retry")
                .build();
    }

    @Bean
    public Binding retryWaitBinding(Queue retryWaitQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryWaitQueue).to(retryExchange).with(queueName + ".wait");
    }

    // Parking lot for messages that exhausted all retries
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(queueName + ".dead").build();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
