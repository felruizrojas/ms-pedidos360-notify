package com.pedidos360.notify.processing;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.config.MailProperties;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * invoice.gen: registra en el log la boleta del pedido entregado (ítems, subtotales y total) y se la envía
 * al cliente en un correo propio con la boleta en PDF adjunta. Con pedidos360.mail.enabled=false (modo demo)
 * el PDF se genera igual pero no se envía. El aviso "Tu pedido fue entregado" lo manda email.send aparte.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProcessor implements ProcesadorComando {

    private static final String LINEA = "-".repeat(56);

    private final MailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final BoletaPdf boletaPdf;

    @Override
    public String nombre() {
        return "invoice";
    }

    @Override
    public Set<String> tiposAceptados() {
        return Set.of("InvoiceRequested");
    }

    @Override
    public void procesar(CommandEnvelope envelope) {
        // Formato.payload ya rechaza (DLQ) un total que no coincide con la suma de los subtotales.
        PedidoComandoPayload p = Formato.payload(envelope);
        log.info("Boleta generada:\n{}", construir(envelope));
        enviarPdf(p);
    }

    private void enviarPdf(PedidoComandoPayload p) {
        byte[] pdf = boletaPdf.generar(p);
        String archivo = "boleta-pedido-" + p.pedidoId() + ".pdf";
        if (!mailProperties.enabled()) {
            log.info("[modo demo, MAIL_ENABLED=false] Boleta PDF no enviada: {} ({} bytes)", archivo, pdf.length);
            return;
        }
        // La boleta ya quedó en el log; sin destinatario no hay a quién enviar el PDF (no es motivo de DLQ).
        if (p.clienteEmail() == null || p.clienteEmail().isBlank()) {
            log.warn("El pedido {} no trae clienteEmail: la boleta PDF no se envía", p.pedidoId());
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("MAIL_ENABLED=true pero no hay JavaMailSender (falta MAIL_HOST)");
        }
        MimeMessage mensaje = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(mailProperties.from());
            helper.setTo(p.clienteEmail());
            helper.setSubject("Boleta de tu pedido #" + p.pedidoId());
            helper.setText("Hola " + (p.clienteNombre() == null ? "cliente" : p.clienteNombre()) + ",\n\n"
                    + "Adjuntamos la boleta de tu pedido #" + p.pedidoId() + " por un total de "
                    + Formato.pesos(p.total()) + ".\n\nEquipo Pedidos360");
            helper.addAttachment(archivo, new ByteArrayResource(pdf), "application/pdf");
        } catch (MessagingException ex) {
            throw new IllegalStateException("No se pudo armar el correo de la boleta del pedido " + p.pedidoId(), ex);
        }
        sender.send(mensaje);
        log.info("Boleta PDF enviada a {}: {}", p.clienteEmail(), archivo);
    }

    public String construir(CommandEnvelope envelope) {
        PedidoComandoPayload p = Formato.payload(envelope);
        StringBuilder b = new StringBuilder()
                .append("======================== BOLETA ========================\n")
                .append("Pedido #").append(p.pedidoId()).append("   Fecha: ").append(Formato.fecha(p.fecha())).append('\n')
                .append("Cliente: ").append(p.clienteNombre()).append(" <").append(p.clienteEmail()).append(">\n")
                .append(LINEA).append('\n')
                .append(String.format("%4s  %-24s %11s %11s%n", "Cant", "Producto", "P. unit.", "Subtotal"));
        for (PedidoComandoPayload.Item i : p.items()) {
            b.append(String.format("%4d  %-24s %11s %11s%n", i.cantidad(), recortar(i.productoNombre()),
                    Formato.pesos(i.precioUnitario()), Formato.pesos(i.subtotal())));
        }
        return b.append(LINEA).append('\n')
                .append(String.format("%-42s %11s%n", "TOTAL", Formato.pesos(p.total())))
                .append("========================================================")
                .toString();
    }

    private static String recortar(String nombre) {
        if (nombre == null) {
            return "";
        }
        return nombre.length() > 24 ? nombre.substring(0, 23) + "…" : nombre;
    }
}
