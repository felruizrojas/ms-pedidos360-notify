package com.pedidos360.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.pedidos360.notify.Ejemplos;
import com.rabbitmq.client.Channel;

class DlqListenerTest {

    private final DlqListener listener = new DlqListener(Ejemplos.JSON);

    /** Como llega a q.cmd.email.dlq después de un basicNack(requeue=false) en q.cmd.email. */
    private static Message muerto(String body) {
        Message m = Ejemplos.mensaje(body, 21);
        MessageProperties props = m.getMessageProperties();
        props.setConsumerQueue("q.cmd.email.dlq");
        props.setHeader("x-death", List.of(Map.of(
                "queue", "q.cmd.email",
                "reason", "rejected",
                "count", 2L,
                "exchange", "cmd.direct",
                "routing-keys", List.of("email.send"),
                "time", new Date())));
        props.setHeader("x-first-death-queue", "q.cmd.email");
        props.setHeader("x-first-death-reason", "rejected");
        props.setHeader("x-first-death-exchange", "cmd.direct");
        return m;
    }

    @Test
    void leeXDeathYLosIdsDelSobre() {
        DlqListener.DeadLetter dl = listener.leer(muerto(Ejemplos.ENVELOPE_JSON));

        assertThat(dl.colaOrigen()).isEqualTo("q.cmd.email");
        assertThat(dl.exchangeOrigen()).isEqualTo("cmd.direct");
        assertThat(dl.routingKey()).isEqualTo("email.send");
        assertThat(dl.motivo()).isEqualTo("rejected");
        assertThat(dl.veces()).isEqualTo(2);
        assertThat(dl.eventId()).isEqualTo("0f8c7a52-3c1e-4a7e-9d43-6a1f0b9e2c11");
        assertThat(dl.correlationId()).isEqualTo("req-123");
        assertThat(dl.body()).contains("\"pedidoId\": 15");
    }

    @Test
    void conCuerpoNoJsonRegistraElCuerpoCrudo() {
        DlqListener.DeadLetter dl = listener.leer(muerto("{esto no es json"));

        assertThat(dl.colaOrigen()).isEqualTo("q.cmd.email");
        assertThat(dl.eventId()).isNull();
        assertThat(dl.correlationId()).isEqualTo("req-123");
        assertThat(dl.body()).isEqualTo("{esto no es json");
    }

    @Test
    void sinXDeathIgualHaceAck() throws Exception {
        Channel channel = mock(Channel.class);
        Message m = Ejemplos.mensaje("hola", 22);

        listener.registrar(m, channel);

        verify(channel).basicAck(22, false);
    }

    @Test
    void registraYHaceAck() throws Exception {
        Channel channel = mock(Channel.class);

        listener.registrar(muerto(Ejemplos.ENVELOPE_JSON), channel);

        verify(channel).basicAck(21, false);
    }
}
