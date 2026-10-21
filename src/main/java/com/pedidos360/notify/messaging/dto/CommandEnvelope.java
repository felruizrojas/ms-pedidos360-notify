package com.pedidos360.notify.messaging.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Sobre común de los comandos que publica orders (ver "Contrato para otros servicios" en su README).
 * DTO propio de notify: se ignoran campos desconocidos para tolerar cambios compatibles del contrato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        PedidoComandoPayload payload) {
}
