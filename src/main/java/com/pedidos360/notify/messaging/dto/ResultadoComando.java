package com.pedidos360.notify.messaging.dto;

import java.time.Instant;

/**
 * Vuelta notify -> orders (evt.direct, routing key resultado.comando): qué pasó con cada comando.
 * resultado: PROCESADO (ACK), FALLIDO (NACK, con el motivo) o EN_DLQ (llegó a la DLQ: rechazado o expirado).
 * comandoEventId es el eventId del comando original: orders lo usa para asociarlo a lo que publicó.
 */
public record ResultadoComando(
        String eventId,
        String comandoEventId,
        Long pedidoId,
        String comando,
        String type,
        String resultado,
        String motivo,
        String cola,
        Instant fecha,
        String correlationId) {
}
