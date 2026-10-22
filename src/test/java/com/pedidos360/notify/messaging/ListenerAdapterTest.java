package com.pedidos360.notify.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pedidos360.notify.Ejemplos;
import com.rabbitmq.client.Channel;

/**
 * Pasa por el adaptador real de @RabbitListener (el que usa el contenedor): un mensaje con el __TypeId__
 * de orders o con JSON inválido debe llegar al listener y terminar en ACK/NACK, nunca quedar unacked.
 */
@SpringBootTest
@ActiveProfiles("test")
class ListenerAdapterTest {

    @Autowired
    RabbitListenerEndpointRegistry registry;

    private void entregar(String listenerId, String body, long tag, Channel channel) throws Exception {
        var container = (AbstractMessageListenerContainer) registry.getListenerContainer(listenerId);
        ((ChannelAwareMessageListener) container.getMessageListener()).onMessage(Ejemplos.mensaje(body, tag), channel);
    }

    @Test
    void mensajeDeOrdersConTypeIdAjenoHaceAck() throws Exception {
        Channel channel = mock(Channel.class);
        entregar("kitchen", Ejemplos.envelopeJson("adapter-1", "CREADO", "ACEPTADO"), 31, channel);
        verify(channel).basicAck(31, false);
    }

    @Test
    void jsonInvalidoHaceNackDesdeElAdaptador() throws Exception {
        Channel channel = mock(Channel.class);
        entregar("email", "{esto no es json", 32, channel);
        verify(channel).basicNack(32, false, false);
    }

    @Test
    void notificacionDePruebaEnEmailHaceAck() throws Exception {
        Channel channel = mock(Channel.class);
        var container = (AbstractMessageListenerContainer) registry.getListenerContainer("email");
        ((ChannelAwareMessageListener) container.getMessageListener())
                .onMessage(NotificationConsumerTest.notificacion(NotificationConsumerTest.NOTIFICACION_JSON, 34), channel);
        verify(channel).basicAck(34, false);
    }

    @Test
    void elListenerDeDlqHaceAck() throws Exception {
        Channel channel = mock(Channel.class);
        entregar("invoice-dlq", "cualquier cosa", 33, channel);
        verify(channel).basicAck(33, false);
    }
}
