package org.openmrs.module.azaricomm.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("azaricomm.RabbitMQService")
public class RabbitMQService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQService.class);

    private Connection connection;
    private Channel channel;
    private String exchangeName;

    @Override
    public void afterPropertiesSet() {
        try {
            String host = gp("azaricomm.rabbitmq.host", "localhost");
            int port = Integer.parseInt(gp("azaricomm.rabbitmq.port", "5672"));
            String username = gp("azaricomm.rabbitmq.username", "guest");
            String password = gp("azaricomm.rabbitmq.password", "guest");
            exchangeName = gp("azaricomm.rabbitmq.exchange", "openmrs.events");

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);

            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);

            log.info("AzariComm: RabbitMQ connected to {}:{}", host, port);
        } catch (Exception e) {
            log.error("AzariComm: Failed to connect to RabbitMQ", e);
        }
    }

    public boolean isConnected() {
        return connection != null && connection.isOpen()
                && channel != null && channel.isOpen();
    }

    public void publish(String routingKey, String message) throws IOException {
        if (!isConnected()) {
            log.warn("AzariComm: RabbitMQ not connected, dropping message: {}", routingKey);
            return;
        }
        channel.basicPublish(exchangeName, routingKey, null, message.getBytes("UTF-8"));
        log.debug("AzariComm: Published to {}: {}", routingKey, message);
    }

    @Override
    public void destroy() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
            log.info("AzariComm: RabbitMQ connection closed");
        } catch (Exception e) {
            log.warn("AzariComm: Error closing RabbitMQ connection", e);
        }
    }

    private String gp(String key, String defaultValue) {
        return Context.getAdministrationService().getGlobalProperty(key, defaultValue);
    }
}
