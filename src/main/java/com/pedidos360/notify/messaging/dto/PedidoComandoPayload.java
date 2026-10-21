package com.pedidos360.notify.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload igual para email.send, kitchen.ticket e invoice.gen. Los estados se leen como texto
 * (CREADO, ACEPTADO, ...) para no acoplar notify al enum de orders; estadoAnterior es null al crear.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PedidoComandoPayload(
        Long pedidoId,
        String clienteId,
        String clienteNombre,
        String clienteEmail,
        String estadoAnterior,
        String estadoNuevo,
        BigDecimal total,
        List<Item> items,
        Instant fecha) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Long productoId, String productoNombre, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {
    }
}
