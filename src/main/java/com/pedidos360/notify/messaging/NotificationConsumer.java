package com.pedidos360.notify.messaging;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.messaging.dto.NotificationMessage;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notificación de prueba de la guía Parte 2: llega a q.cmd.email con la propiedad AMQP
 * type=TestNotification y sin el sobre de los comandos. Igual que en la guía, solo se registra en el log
 * (no se envía correo) y se confirma con ACK. Si el JSON no se puede leer: NACK sin reencolar -> DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    public static final String TYPE_TEST_NOTIFICATION = "TestNotification";

    private static final ParameterizedTypeReference<NotificationMessage> TIPO = new ParameterizedTypeReference<>() {
    };

    private final SmartMessageConverter converter;

    public static boolean esNotificacionPrueba(Message message) {
        return TYPE_TEST_NOTIFICATION.equals(message.getMessageProperties().getType());
    }

    public void consumir(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        NotificationMessage mensaje;
        try {
            Object convertido = converter.fromMessage(message, TIPO);
            if (!(convertido instanceof NotificationMessage n)) {
                throw new MensajeInvalidoException("El cuerpo no es JSON (content_type="
                        + message.getMessageProperties().getContentType() + ")");
            }
            mensaje = n;
        } catch (MessageConversionException | MensajeInvalidoException ex) {
            log.error("[test-notification] Mensaje ilegible: {}. NACK sin reencolar -> DLQ",
                    NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        log.info("Notificacion recibida:");
        log.info("Pedido: {}", mensaje.pedidoId());
        log.info("Destinatario: {}", mensaje.email());
        log.info("Asunto: {}", mensaje.asunto());
        log.info("Mensaje: {}", mensaje.mensaje());
        channel.basicAck(deliveryTag, false);
        log.info("[test-notification] ACK messageId={}", message.getMessageProperties().getMessageId());
    }
}
