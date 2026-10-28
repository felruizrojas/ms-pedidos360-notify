package com.pedidos360.notify.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.config.RabbitProperties;
import com.pedidos360.notify.messaging.dto.ResultadoComando;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica en RabbitMQ el resultado de cada comando para que orders lo registre por pedido (dashboard del
 * operador). Si RabbitMQ no está, se deja en el log y no afecta al ACK/NACK del comando.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicadorResultados {

    public static final String PROCESADO = "PROCESADO";
    public static final String FALLIDO = "FALLIDO";
    public static final String EN_DLQ = "EN_DLQ";

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties properties;

    public void publicar(String comandoEventId, Long pedidoId, String comando, String type, String resultado,
            String motivo, String cola, String correlationId) {
        ResultadoComando r = new ResultadoComando(UUID.randomUUID().toString(), comandoEventId, pedidoId, comando,
                type, resultado, motivo, cola, Instant.now(), correlationId);
        RabbitProperties.Resultados destino = properties.resultados();
        try {
            rabbitTemplate.convertAndSend(destino.exchange(), destino.routingKey(), r, message -> {
                MessageProperties props = message.getMessageProperties();
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setType("CommandResult");
                if (correlationId != null) {
                    props.setCorrelationId(correlationId);
                }
                return message;
            });
            log.info("[{}] Resultado {} enviado a orders: pedidoId={} comandoEventId={}", comando, resultado, pedidoId,
                    comandoEventId);
        } catch (AmqpException ex) {
            log.warn("[{}] No se pudo enviar el resultado {} a orders (pedidoId={}): {}", comando, resultado, pedidoId,
                    ex.getMessage());
        }
    }
}
