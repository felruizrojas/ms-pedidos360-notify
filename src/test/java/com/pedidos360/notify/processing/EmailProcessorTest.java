package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.pedidos360.notify.Ejemplos;
import com.pedidos360.notify.config.MailProperties;
import com.pedidos360.notify.messaging.MensajeInvalidoException;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

class EmailProcessorTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private EmailProcessor processor(boolean enabled) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new EmailProcessor(new MailProperties(enabled, "no-reply@pedidos360.cl"), provider);
    }

    @Test
    void armaElEmailDelEnvelopeDeEjemplo() {
        EmailProcessor.Email email = processor(false).construir(Ejemplos.envelope());

        assertThat(email.para()).isEqualTo("maria@contoso.cl");
        assertThat(email.asunto()).isEqualTo("Tu pedido #15 fue aceptado");
        assertThat(email.cuerpo())
                .startsWith("Hola María Pérez,")
                .contains("Pedido: #15", "Estado: CREADO -> ACEPTADO", "Fecha: 20-10-2026 12:10",
                        "2 x Pan Amasado ($1.200)", "1 x Café Americano ($1.500)", "Total: $2.700");
    }

    @Test
    void elAsuntoDependeDelEstado() {
        EmailProcessor p = processor(false);
        assertThat(p.construir(Ejemplos.envelope(null, "CREADO")).asunto()).isEqualTo("Recibimos tu pedido #15");
        assertThat(p.construir(Ejemplos.envelope(null, "CREADO")).cuerpo()).contains("Estado: CREADO\n");
        assertThat(p.construir(Ejemplos.envelope("ACEPTADO", "EN_PREPARACION")).asunto()).isEqualTo("Tu pedido #15 está en preparación");
        assertThat(p.construir(Ejemplos.envelope("EN_PREPARACION", "DESPACHADO")).asunto()).isEqualTo("Tu pedido #15 va en camino");
        assertThat(p.construir(Ejemplos.envelope("DESPACHADO", "ENTREGADO")).asunto()).isEqualTo("Tu pedido #15 fue entregado");
        assertThat(p.construir(Ejemplos.envelope("CREADO", "CANCELADO")).asunto()).isEqualTo("Tu pedido #15 fue cancelado");
    }

    @Test
    void enModoDemoNoEnvia() {
        processor(false).procesar(Ejemplos.envelope());
        verifyNoInteractions(sender);
    }

    @Test
    void conMailHabilitadoEnviaPorSmtp() {
        processor(true).procesar(Ejemplos.envelope());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("maria@contoso.cl");
        assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@pedidos360.cl");
        assertThat(captor.getValue().getSubject()).isEqualTo("Tu pedido #15 fue aceptado");
    }

    @Test
    void sinEmailDelClienteEsMensajeInvalido() {
        CommandEnvelope e = Ejemplos.envelope();
        PedidoComandoPayload p = e.payload();
        CommandEnvelope sinEmail = new CommandEnvelope(e.type(), e.eventId(), e.timestamp(), e.traceId(), e.correlationId(),
                new PedidoComandoPayload(p.pedidoId(), p.clienteId(), p.clienteNombre(), null, p.estadoAnterior(),
                        p.estadoNuevo(), p.total(), p.items(), p.fecha()));

        assertThatThrownBy(() -> processor(false).construir(sinEmail)).isInstanceOf(MensajeInvalidoException.class);
    }
}
