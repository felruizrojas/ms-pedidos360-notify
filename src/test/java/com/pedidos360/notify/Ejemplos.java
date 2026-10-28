package com.pedidos360.notify;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.pedidos360.notify.messaging.dto.CommandEnvelope;

import tools.jackson.databind.json.JsonMapper;

/** Envelope de ejemplo copiado del contrato de ms-pedidos360-orders (README, "Mensajes RabbitMQ"). */
public final class Ejemplos {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    public static final String ENVELOPE_JSON = """
            {
              "type": "OrderStatusChanged",
              "eventId": "0f8c7a52-3c1e-4a7e-9d43-6a1f0b9e2c11",
              "timestamp": "2026-10-20T15:10:00.789Z",
              "traceId": "req-123",
              "correlationId": "req-123",
              "payload": {
                "pedidoId": 15,
                "clienteId": "4f6c2a1e-1111-2222-3333-444455556666",
                "clienteNombre": "María Pérez",
                "clienteEmail": "maria@contoso.cl",
                "estadoAnterior": "CREADO",
                "estadoNuevo": "ACEPTADO",
                "total": 2700.00,
                "items": [
                  { "productoId": 1, "productoNombre": "Pan Amasado", "cantidad": 2, "precioUnitario": 600.00, "subtotal": 1200.00 },
                  { "productoId": 4, "productoNombre": "Café Americano", "cantidad": 1, "precioUnitario": 1500.00, "subtotal": 1500.00 }
                ],
                "fecha": "2026-10-20T15:10:00.456Z"
              }
            }
            """;

    private Ejemplos() {
    }

    public static CommandEnvelope envelope() {
        return JSON.readValue(ENVELOPE_JSON, CommandEnvelope.class);
    }

    /** Mismo envelope cambiando eventId y estados (estadoAnterior null = OrderCreated). */
    public static String envelopeJson(String eventId, String estadoAnterior, String estadoNuevo) {
        return ENVELOPE_JSON
                .replace("0f8c7a52-3c1e-4a7e-9d43-6a1f0b9e2c11", eventId)
                .replace("\"estadoAnterior\": \"CREADO\"",
                        "\"estadoAnterior\": " + (estadoAnterior == null ? "null" : "\"" + estadoAnterior + "\""))
                .replace("\"estadoNuevo\": \"ACEPTADO\"", "\"estadoNuevo\": \"" + estadoNuevo + "\"");
    }

    /** Mismo JSON cambiando el "type" del sobre. */
    public static String conType(String json, String type) {
        return json.replace("\"type\": \"OrderStatusChanged\"", "\"type\": \"" + type + "\"");
    }

    public static CommandEnvelope envelope(String estadoAnterior, String estadoNuevo) {
        return JSON.readValue(envelopeJson("e-1", estadoAnterior, estadoNuevo), CommandEnvelope.class);
    }

    /** Mensaje AMQP como lo publica orders (incluye el __TypeId__ con su clase Java, que notify ignora). */
    public static Message mensaje(String body, long deliveryTag) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryTag(deliveryTag);
        props.setCorrelationId("req-123");
        props.setConsumerQueue("q.cmd.email");
        props.setReceivedRoutingKey("email.send");
        props.setHeader("__TypeId__", "com.pedidos360.orders.messaging.rabbit.CommandEnvelope");
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
