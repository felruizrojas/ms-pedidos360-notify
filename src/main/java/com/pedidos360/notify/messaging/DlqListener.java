package com.pedidos360.notify.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra los mensajes no entregados (rúbrica: "registrar los mensajes no entregados desde las DLQ").
 * Lee el header x-death que agrega RabbitMQ (cola de origen, motivo, cantidad de veces) y hace ACK:
 * el log.error es la evidencia; el mensaje no se reprocesa. Además avisa a orders por RabbitMQ (resultado EN_DLQ),
 * lo que cubre también los mensajes que expiraron sin que notify los viera (TTL de la cola).
 * Con NOTIFY_DLQ_LISTENERS=false no arrancan, y los mensajes quedan visibles en la DLQ (útil en la demo).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqListener {

    private static final String AUTO_STARTUP = "${pedidos360.consumidor.dlq-listeners:true}";
    private static final int MAX_BODY = 4000;

    private final JsonMapper jsonMapper;
    private final PublicadorResultados resultados;

    public record DeadLetter(String colaOrigen, String exchangeOrigen, String routingKey, String motivo, long veces,
            String eventId, String correlationId, String body, Long pedidoId, String type) {
    }

    @RabbitListener(id = "email-dlq", queues = "${pedidos360.rabbit.queues.email}.dlq", autoStartup = AUTO_STARTUP)
    public void onEmailDlq(Message message, Channel channel) throws IOException {
        registrar(message, channel);
    }

    @RabbitListener(id = "kitchen-dlq", queues = "${pedidos360.rabbit.queues.kitchen}.dlq", autoStartup = AUTO_STARTUP)
    public void onKitchenDlq(Message message, Channel channel) throws IOException {
        registrar(message, channel);
    }

    @RabbitListener(id = "invoice-dlq", queues = "${pedidos360.rabbit.queues.invoice}.dlq", autoStartup = AUTO_STARTUP)
    public void onInvoiceDlq(Message message, Channel channel) throws IOException {
        registrar(message, channel);
    }

    void registrar(Message message, Channel channel) throws IOException {
        MessageProperties props = message.getMessageProperties();
        try {
            DeadLetter dl = leer(message);
            MDC.put(ConsumidorComandos.MDC_CORRELATION_ID, dl.correlationId());
            log.error("MENSAJE NO ENTREGADO en {}: colaOrigen={} exchange={} routingKey={} motivo={} veces={} "
                    + "eventId={} correlationId={} body={}", props.getConsumerQueue(), dl.colaOrigen(),
                    dl.exchangeOrigen(), dl.routingKey(), dl.motivo(), dl.veces(), dl.eventId(), dl.correlationId(),
                    dl.body());
            if (dl.eventId() != null) {
                resultados.publicar(dl.eventId(), dl.pedidoId(), comando(props.getConsumerQueue()), dl.type(),
                        PublicadorResultados.EN_DLQ, motivoLegible(dl.motivo()), dl.colaOrigen(), dl.correlationId());
            }
        } catch (RuntimeException ex) {
            // Nunca dejar el mensaje sin ACK en la DLQ: se registra lo que se pueda y se confirma igual.
            log.error("MENSAJE NO ENTREGADO en {} (no se pudo leer x-death: {}): body={}", props.getConsumerQueue(),
                    ex.getMessage(), new String(message.getBody(), StandardCharsets.UTF_8), ex);
        } finally {
            MDC.remove(ConsumidorComandos.MDC_CORRELATION_ID);
        }
        channel.basicAck(props.getDeliveryTag(), false);
    }

    public DeadLetter leer(Message message) {
        MessageProperties props = message.getMessageProperties();
        List<Map<String, ?>> xDeath = props.getXDeathHeader();
        if (xDeath == null) {
            xDeath = props.getHeader(MessageProperties.X_DEATH);
        }
        // x-death trae una entrada por (cola, motivo), la más reciente primero.
        Map<String, ?> ultima = xDeath == null || xDeath.isEmpty() ? Map.of() : xDeath.getFirst();

        String cola = texto(props.getHeaders().get("x-first-death-queue"), ultima.get("queue"));
        String motivo = texto(props.getHeaders().get("x-first-death-reason"), ultima.get("reason"));
        String exchange = texto(ultima.get("exchange"), props.getHeaders().get("x-first-death-exchange"));
        String routingKey = ultima.get("routing-keys") instanceof List<?> rks && !rks.isEmpty()
                ? String.valueOf(rks.getFirst())
                : props.getReceivedRoutingKey();
        long veces = ultima.get("count") instanceof Number n ? n.longValue() : 0;

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String eventId = null;
        String correlationId = props.getCorrelationId();
        Long pedidoId = null;
        String type = null;
        try {
            JsonNode json = jsonMapper.readTree(body);
            eventId = campo(json, "eventId");
            type = campo(json, "type");
            JsonNode id = json.path("payload").path("pedidoId");
            pedidoId = id.isNumber() ? id.asLong() : null;
            String cid = campo(json, "correlationId");
            if (cid != null) {
                correlationId = cid;
            }
        } catch (RuntimeException ex) {
            // El cuerpo puede no ser JSON (justamente por eso llegó a la DLQ): se registra crudo.
        }
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY) + "...(truncado)";
        }
        return new DeadLetter(cola, exchange, routingKey, motivo, veces, eventId, correlationId, body, pedidoId, type);
    }

    /** q.cmd.kitchen.dlq -> kitchen. */
    static String comando(String colaDlq) {
        if (colaDlq == null) {
            return null;
        }
        String sinDlq = colaDlq.endsWith(".dlq") ? colaDlq.substring(0, colaDlq.length() - 4) : colaDlq;
        return sinDlq.substring(sinDlq.lastIndexOf('.') + 1);
    }

    private static String motivoLegible(String reason) {
        if (reason == null) {
            return "Llegó a la DLQ";
        }
        return switch (reason) {
            case "rejected" -> "Rechazado (NACK): quedó en la DLQ";
            case "expired" -> "Expiró sin que nadie lo consumiera (TTL): quedó en la DLQ";
            default -> "Llegó a la DLQ (" + reason + ")";
        };
    }

    private static String campo(JsonNode json, String nombre) {
        JsonNode valor = json == null ? null : json.get(nombre);
        return valor == null || valor.isNull() ? null : valor.asString();
    }

    private static String texto(Object preferido, Object alternativo) {
        Object valor = preferido != null ? preferido : alternativo;
        return valor == null ? null : valor.toString();
    }
}
