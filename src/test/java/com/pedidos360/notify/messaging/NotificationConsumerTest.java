package com.pedidos360.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import com.pedidos360.notify.Ejemplos;
import com.rabbitmq.client.Channel;

class NotificationConsumerTest {

    static final String NOTIFICACION_JSON = """
            {"pedidoId":501,"email":"cliente@ejemplo.cl","asunto":"Pedido confirmado","mensaje":"Su pedido 501 ha sido confirmado"}
            """;

    private final Channel channel = mock(Channel.class);
    private final NotificationConsumer consumidor = new NotificationConsumer(new JacksonJsonMessageConverter(Ejemplos.JSON));

    /** Como la publica orders: type=TestNotification y __TypeId__ con la clase de orders. */
    static Message notificacion(String body, long tag) {
        Message m = Ejemplos.mensaje(body, tag);
        m.getMessageProperties().setType(NotificationConsumer.TYPE_TEST_NOTIFICATION);
        m.getMessageProperties().setHeader("__TypeId__", "com.pedidos360.orders.dto.NotificationMessage");
        return m;
    }

    @Test
    void reconoceLaNotificacionPorElTypeAmqp() {
        assertThat(NotificationConsumer.esNotificacionPrueba(notificacion(NOTIFICACION_JSON, 1))).isTrue();
        assertThat(NotificationConsumer.esNotificacionPrueba(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 2))).isFalse();
    }

    @Test
    void notificacionValidaHaceAck() throws Exception {
        consumidor.consumir(notificacion(NOTIFICACION_JSON, 41), channel);

        verify(channel).basicAck(41, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void jsonInvalidoHaceNackSinReencolar() throws Exception {
        consumidor.consumir(notificacion("{esto no es json", 42), channel);

        verify(channel).basicNack(42, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
