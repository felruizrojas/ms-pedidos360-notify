package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import com.pedidos360.notify.Ejemplos;
import com.pedidos360.notify.config.MailProperties;
import com.pedidos360.notify.messaging.MensajeInvalidoException;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class InvoiceProcessorTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private InvoiceProcessor processor(boolean enabled) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        return new InvoiceProcessor(new MailProperties(enabled, "no-reply@pedidos360.cl"), provider, new BoletaPdf());
    }

    @Test
    void armaLaBoletaDelEnvelopeDeEjemplo() {
        String boleta = processor(false).construir(Ejemplos.envelope("DESPACHADO", "ENTREGADO"));

        assertThat(boleta)
                .contains("BOLETA", "Pedido #15", "Cliente: María Pérez <maria@contoso.cl>")
                .containsPattern("2  Pan Amasado\\s+\\$600\\s+\\$1\\.200")
                .containsPattern("1  Café Americano\\s+\\$1\\.500\\s+\\$1\\.500")
                .containsPattern("TOTAL\\s+\\$2\\.700");
    }

    @Test
    void sinItemsEsMensajeInvalido() {
        String json = Ejemplos.ENVELOPE_JSON.replaceAll("(?s)\"items\": \\[.*?\\]", "\"items\": []");
        var envelope = Ejemplos.JSON.readValue(json, CommandEnvelope.class);

        assertThatThrownBy(() -> processor(false).construir(envelope)).isInstanceOf(MensajeInvalidoException.class);
    }

    @Test
    void enModoDemoNoEnviaLaBoleta() {
        processor(false).procesar(Ejemplos.envelope("DESPACHADO", "ENTREGADO"));
        verifyNoInteractions(sender);
    }

    @Test
    void conMailHabilitadoEnviaLaBoletaEnPdfAdjunta() throws Exception {
        processor(true).procesar(Ejemplos.envelope("DESPACHADO", "ENTREGADO"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage mensaje = captor.getValue();
        mensaje.saveChanges();
        assertThat(mensaje.getRecipients(Message.RecipientType.TO)).extracting(Object::toString)
                .containsExactly("maria@contoso.cl");
        assertThat(mensaje.getFrom()).extracting(Object::toString).containsExactly("no-reply@pedidos360.cl");
        assertThat(mensaje.getSubject()).isEqualTo("Boleta de tu pedido #15");

        BodyPart adjunto = adjunto((Multipart) mensaje.getContent());
        assertThat(adjunto.getFileName()).isEqualTo("boleta-pedido-15.pdf");
        assertThat(adjunto.getContentType()).startsWith("application/pdf");
    }

    @Test
    void sinEmailDelClienteNoEnviaPeroNoFalla() {
        CommandEnvelope e = Ejemplos.envelope("DESPACHADO", "ENTREGADO");
        PedidoComandoPayload p = e.payload();
        CommandEnvelope sinEmail = new CommandEnvelope(e.type(), e.eventId(), e.timestamp(), e.traceId(), e.correlationId(),
                new PedidoComandoPayload(p.pedidoId(), p.clienteId(), p.clienteNombre(), null, p.estadoAnterior(),
                        p.estadoNuevo(), p.total(), p.items(), p.fecha()));

        processor(true).procesar(sinEmail);

        verify(sender, never()).send(any(MimeMessage.class));
    }

    /** Busca la parte con disposición "attachment" dentro del multipart (puede venir anidada). */
    private static BodyPart adjunto(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart parte = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(parte.getDisposition())) {
                return parte;
            }
            if (parte.getContent() instanceof Multipart anidado) {
                BodyPart encontrado = adjunto(anidado);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }
}
