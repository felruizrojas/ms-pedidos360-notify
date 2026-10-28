package com.pedidos360.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import com.pedidos360.notify.Ejemplos;
import com.pedidos360.notify.config.ConsumidorProperties;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.processing.ProcesadorComando;
import com.rabbitmq.client.Channel;

class ConsumidorComandosTest {

    private final Channel channel = mock(Channel.class);
    private final ProcesadorComando procesador = mock(ProcesadorComando.class);
    private ConsumidorComandos consumidor;

    @BeforeEach
    void setUp() {
        ConsumidorProperties props = new ConsumidorProperties(3, Duration.ofMillis(1), 100);
        consumidor = new ConsumidorComandos(new JacksonJsonMessageConverter(Ejemplos.JSON), new EventIdCache(props), props);
        when(procesador.nombre()).thenReturn("email");
        when(procesador.tiposAceptados()).thenReturn(Set.of("OrderCreated", "OrderStatusChanged"));
    }

    @Test
    void casoFelizHaceAckYPasaElEnvelopeAlProcesador() throws Exception {
        List<String> correlationIds = new ArrayList<>();
        doAnswer(inv -> correlationIds.add(MDC.get(ConsumidorComandos.MDC_CORRELATION_ID)))
                .when(procesador).procesar(any());

        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 7), channel, procesador);

        ArgumentCaptor<CommandEnvelope> captor = ArgumentCaptor.forClass(CommandEnvelope.class);
        verify(procesador).procesar(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("0f8c7a52-3c1e-4a7e-9d43-6a1f0b9e2c11");
        assertThat(captor.getValue().payload().items()).hasSize(2);
        assertThat(correlationIds).containsExactly("req-123");
        assertThat(MDC.get(ConsumidorComandos.MDC_CORRELATION_ID)).isNull();
        verify(channel).basicAck(7, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void errorSimuladoHaceNackSinReencolar() throws Exception {
        Message m = Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 8);
        m.getMessageProperties().setHeader(ConsumidorComandos.HEADER_SIMULAR_ERROR, "true");

        consumidor.consumir(m, channel, procesador);

        verify(procesador, never()).procesar(any());
        verify(channel).basicNack(8, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void errorDeProcesamientoSeReintentaYLuegoNack() throws Exception {
        doThrow(new IllegalStateException("SMTP caído")).when(procesador).procesar(any());

        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 9), channel, procesador);

        verify(procesador, times(3)).procesar(any());
        verify(channel).basicNack(9, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void errorTransitorioQueSeRecuperaHaceAck() throws Exception {
        doThrow(new IllegalStateException("SMTP caído")).doNothing().when(procesador).procesar(any());

        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 10), channel, procesador);

        verify(procesador, times(2)).procesar(any());
        verify(channel).basicAck(10, false);
    }

    @Test
    void jsonInvalidoHaceNackSinReintentar() throws Exception {
        consumidor.consumir(Ejemplos.mensaje("{esto no es json", 11), channel, procesador);

        verify(procesador, never()).procesar(any());
        verify(channel).basicNack(11, false, false);
    }

    @Test
    void sinEventIdHaceNack() throws Exception {
        String json = Ejemplos.ENVELOPE_JSON.replace("\"eventId\": \"0f8c7a52-3c1e-4a7e-9d43-6a1f0b9e2c11\",", "");

        consumidor.consumir(Ejemplos.mensaje(json, 12), channel, procesador);

        verify(procesador, never()).procesar(any());
        verify(channel).basicNack(12, false, false);
    }

    @Test
    void typeDeOtraColaHaceNackSinProcesar() throws Exception {
        String json = Ejemplos.conType(Ejemplos.ENVELOPE_JSON, "KitchenTicketRequested");

        consumidor.consumir(Ejemplos.mensaje(json, 18), channel, procesador);

        verify(procesador, never()).procesar(any());
        verify(channel).basicNack(18, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void sinTypeHaceNack() throws Exception {
        String json = Ejemplos.ENVELOPE_JSON.replace("\"type\": \"OrderStatusChanged\",", "");

        consumidor.consumir(Ejemplos.mensaje(json, 19), channel, procesador);

        verify(procesador, never()).procesar(any());
        verify(channel).basicNack(19, false, false);
    }

    @Test
    void mensajeInvalidoDelProcesadorNoSeReintenta() throws Exception {
        doThrow(new MensajeInvalidoException("sin items")).when(procesador).procesar(any());

        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 13), channel, procesador);

        verify(procesador, times(1)).procesar(any());
        verify(channel).basicNack(13, false, false);
    }

    @Test
    void eventIdDuplicadoHaceAckSinReprocesar() throws Exception {
        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 14), channel, procesador);
        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 15), channel, procesador);

        verify(procesador, times(1)).procesar(any());
        verify(channel).basicAck(14, false);
        verify(channel).basicAck(15, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void unMensajeFallidoNoQuedaComoProcesado() throws Exception {
        Message conError = Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 16);
        conError.getMessageProperties().setHeader(ConsumidorComandos.HEADER_SIMULAR_ERROR, true);
        consumidor.consumir(conError, channel, procesador);

        consumidor.consumir(Ejemplos.mensaje(Ejemplos.ENVELOPE_JSON, 17), channel, procesador);

        verify(channel).basicNack(16, false, false);
        verify(procesador, times(1)).procesar(any());
        verify(channel).basicAck(17, false);
    }

    @Test
    void elCacheDeEventIdsEstaAcotado() {
        EventIdCache cache = new EventIdCache(new ConsumidorProperties(1, Duration.ZERO, 2));
        cache.registrar("a");
        cache.registrar("b");
        cache.registrar("c");

        assertThat(cache.tamano()).isEqualTo(2);
        assertThat(cache.yaProcesado("a")).isFalse();
        assertThat(cache.yaProcesado("c")).isTrue();
    }
}
