package com.pedidos360.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Envío de correos (pedidos360.mail.*). La conexión SMTP va en spring.mail.*.
 *
 * @param enabled false (default): modo demo, el email solo se registra en el log
 * @param from    remitente
 */
@ConfigurationProperties("pedidos360.mail")
public record MailProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("no-reply@pedidos360.cl") String from) {
}
