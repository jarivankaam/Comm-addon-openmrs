package com.azaricomm.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange:openmrs.events}")
    private String exchangeName;

    @Value("${rabbitmq.queue:azaricomm.notifications}")
    private String queueName;

    @Value("${rabbitmq.routing-key:notification.#}")
    private String routingKey;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        // Durable queue survives broker restarts
        return new Queue(queueName, true);
    }

    @Bean
    public Binding binding(Queue notificationQueue, TopicExchange exchange) {
        // notification.# matches notification.REMINDER_24H, notification.CANCELLATION, etc.
        return BindingBuilder.bind(notificationQueue).to(exchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
