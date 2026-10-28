package com.pedidos360.notify.processing;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.config.MailProperties;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * email.send: arma el correo al cliente según el type del comando (creado, cambio de estado, modificado o
 * eliminado) y el estado nuevo del pedido. Con
 * pedidos360.mail.enabled=true lo envía por SMTP; si no (modo demo, default) lo registra completo en el log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailProcessor implements ProcesadorComando {

    public record Email(String para, String asunto, String cuerpo) {
    }

    static final String TYPE_CREADO = "OrderCreated";
    static final String TYPE_ESTADO = "OrderStatusChanged";
    static final String TYPE_MODIFICADO = "OrderUpdated";
    static final String TYPE_ELIMINADO = "OrderDeleted";

    private final MailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Override
    public String nombre() {
        return "email";
    }

    @Override
    public Set<String> tiposAceptados() {
        return Set.of(TYPE_CREADO, TYPE_ESTADO, TYPE_MODIFICADO, TYPE_ELIMINADO);
    }

    @Override
    public void procesar(CommandEnvelope envelope) {
        Email email = construir(envelope);
        if (!mailProperties.enabled()) {
            log.info("[modo demo, MAIL_ENABLED=false] Email no enviado:\nDe: {}\nPara: {}\nAsunto: {}\n\n{}",
                    mailProperties.from(), email.para(), email.asunto(), email.cuerpo());
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("MAIL_ENABLED=true pero no hay JavaMailSender (falta MAIL_HOST)");
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(mailProperties.from());
        mensaje.setTo(email.para());
        mensaje.setSubject(email.asunto());
        mensaje.setText(email.cuerpo());
        sender.send(mensaje);
        log.info("Email enviado a {}: {}", email.para(), email.asunto());
    }

    public Email construir(CommandEnvelope envelope) {
        PedidoComandoPayload p = Formato.payload(envelope);
        Formato.exigirEmail(p);
        String estado = p.estadoNuevo() == null ? "" : p.estadoNuevo();
        String[] porType = switch (envelope.type() == null ? "" : envelope.type()) {
            case TYPE_MODIFICADO -> new String[] { "Tu pedido #%d fue modificado",
                    "Tu pedido fue modificado. Este es el detalle actualizado." };
            case TYPE_ELIMINADO -> new String[] { "Tu pedido #%d fue eliminado",
                    "Tu pedido fue eliminado por el equipo de Pedidos360. Si no reconoces esta acción, contáctanos." };
            default -> null;
        };
        String asunto = porType != null ? porType[0] : switch (estado) {
            case "CREADO" -> "Recibimos tu pedido #%d";
            case "ACEPTADO" -> "Tu pedido #%d fue aceptado";
            case "EN_PREPARACION" -> "Tu pedido #%d está en preparación";
            case "DESPACHADO" -> "Tu pedido #%d va en camino";
            case "ENTREGADO" -> "Tu pedido #%d fue entregado";
            case "CANCELADO" -> "Tu pedido #%d fue cancelado";
            default -> "Actualización de tu pedido #%d";
        };
        String detalle = porType != null ? porType[1] : switch (estado) {
            case "CREADO" -> "Recibimos tu pedido. Te avisaremos cuando sea aceptado.";
            case "ACEPTADO" -> "Tu pedido fue aceptado y pronto pasará a cocina.";
            case "EN_PREPARACION" -> "Estamos preparando tu pedido.";
            case "DESPACHADO" -> "Tu pedido salió a reparto y va en camino a tu dirección.";
            case "ENTREGADO" -> "Tu pedido fue entregado. Te enviamos la boleta en PDF en un correo aparte. ¡Gracias por preferirnos!";
            case "CANCELADO" -> "Tu pedido fue cancelado. Si no reconoces esta cancelación, contáctanos.";
            default -> "El estado de tu pedido cambió a " + estado + ".";
        };

        StringBuilder cuerpo = new StringBuilder()
                .append("Hola ").append(p.clienteNombre() == null ? "cliente" : p.clienteNombre()).append(",\n\n")
                .append(detalle).append("\n\n")
                .append("Pedido: #").append(p.pedidoId()).append('\n')
                .append("Estado: ")
                .append(p.estadoAnterior() == null || p.estadoAnterior().equals(estado) ? "" : p.estadoAnterior() + " -> ")
                .append(estado).append('\n')
                .append("Fecha: ").append(Formato.fecha(p.fecha())).append('\n');
        if (p.usuarioNombre() != null) {
            cuerpo.append("Realizado por: ").append(p.usuarioNombre())
                    .append(p.usuarioRol() == null ? "" : " (" + p.usuarioRol() + ")").append('\n');
        }
        if (p.items() != null && !p.items().isEmpty()) {
            cuerpo.append("\nDetalle:\n");
            p.items().forEach(i -> cuerpo.append("  - ").append(i.cantidad()).append(" x ")
                    .append(i.productoNombre()).append(" (").append(Formato.pesos(i.subtotal())).append(")\n"));
        }
        cuerpo.append("Total: ").append(Formato.pesos(p.total())).append("\n\n")
                .append("Equipo Pedidos360");

        return new Email(p.clienteEmail(), asunto.formatted(p.pedidoId()), cuerpo.toString());
    }
}
