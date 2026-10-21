package com.pedidos360.notify.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Comportamiento de los consumidores (pedidos360.consumidor.*).
 *
 * @param maxIntentos     intentos locales antes del NACK (1 = sin reintento)
 * @param esperaReintento pausa entre intentos
 * @param cacheEventIds   cuántos eventId recientes se recuerdan para descartar duplicados
 */
@ConfigurationProperties("pedidos360.consumidor")
public record ConsumidorProperties(
        @DefaultValue("3") int maxIntentos,
        @DefaultValue("200ms") Duration esperaReintento,
        @DefaultValue("10000") int cacheEventIds) {
}
