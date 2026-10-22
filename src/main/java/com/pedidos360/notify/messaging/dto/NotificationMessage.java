package com.pedidos360.notify.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Notificación de prueba de la guía Parte 2 (POST /api/orders/test-notification de orders). Mismo JSON
 * que el NotificationMessage de orders, pero DTO propio: el __TypeId__ de orders se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationMessage(
        Long pedidoId,
        String email,
        String asunto,
        String mensaje) {
}
