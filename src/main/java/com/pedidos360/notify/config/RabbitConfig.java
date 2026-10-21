package com.pedidos360.notify.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Converters de RabbitMQ:
 * <ul>
 * <li>{@code rabbitMessageConverter}: el mismo de orders (Jackson 3 con el JsonMapper de Spring Boot,
 * fechas ISO-8601). Lo usa ConsumidorComandos con un tipo destino fijo, así que el header __TypeId__
 * de orders (su clase Java, que aquí no existe) se ignora.</li>
 * <li>La factory de listeners entrega el cuerpo crudo, sin convertir: si el adaptador de @RabbitListener
 * convirtiera antes de llamar al método, un JSON inválido o un __TypeId__ desconocido fallaría fuera del
 * listener y, con acknowledge-mode manual, el mensaje quedaría sin ACK ni NACK (unacked para siempre).</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /** Misma configuración de Spring Boot (spring.rabbitmq.listener.simple.*), con el converter que no convierte. */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new CuerpoCrudoConverter());
        return factory;
    }

    /** Nunca falla: devuelve el body tal cual; la deserialización real se hace dentro del listener. */
    static class CuerpoCrudoConverter implements MessageConverter {

        @Override
        public Message toMessage(Object object, MessageProperties messageProperties) {
            throw new UnsupportedOperationException("notify no publica mensajes");
        }

        @Override
        public Object fromMessage(Message message) {
            return message.getBody();
        }
    }
}
