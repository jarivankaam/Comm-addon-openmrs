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
import org.springframework.retry.annotation.EnableRetry;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRetry
public class RabbitConfig {

    @Value("${rabbitmq.exchange:azaricomm.events}")
    private String exchangeName;

    @Value("${rabbitmq.queue:azaricomm.notifications}")
    private String queueName;

    @Value("${rabbitmq.routing-key:notification.#}")
    private String routingKey;

    // --- HOOFD INFRASTRUCTUUR ---
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        // Wijs de Dead Letter Exchange en Routing Key aan voor deze specifieke wachtrij
        args.put("x-dead-letter-exchange", "azaricomm.dlx");
        args.put("x-dead-letter-routing-key", "dlq.notification");

        return new Queue(queueName, true, false, false, args);
    }

    @Bean
    public Binding binding(Queue notificationQueue, TopicExchange exchange) {
        return BindingBuilder.bind(notificationQueue).to(exchange).with(routingKey);
    }

    // --- DEAD LETTER QUEUE (DLQ) INFRASTRUCTUUR ---
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("azaricomm.dlx", true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        // De opvangbak voor berichten die na alle backoffs nog steeds falen
        return new Queue("azaricomm.notifications.dlq", true);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("dlq.notification");
    }

    // --- CONVERTERS & TEMPLATES ---
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