package com.pedidos360.notify.messaging;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.config.ConsumidorProperties;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.processing.ProcesadorComando;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lógica común de los 3 consumidores (acknowledge-mode: manual):
 * <ol>
 * <li>correlationId en el MDC (aparece en cada línea del log)</li>
 * <li>deserializa el sobre con el converter JSON (tipo fijo: se ignora __TypeId__)</li>
 * <li>eventId repetido → ACK sin reprocesar</li>
 * <li>procesa con reintento local acotado (max-intentos); x-simular-error=true falla a propósito</li>
 * <li>éxito → basicAck; error → basicNack(requeue=false) y RabbitMQ lo manda a la DLQ por x-dead-letter-exchange</li>
 * </ol>
 * Un mensaje inválido (JSON mal formado, sin eventId o sin datos obligatorios) no se reintenta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumidorComandos {

    public static final String HEADER_SIMULAR_ERROR = "x-simular-error";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_EVENT_ID = "eventId";

    private static final ParameterizedTypeReference<CommandEnvelope> TIPO_ENVELOPE = new ParameterizedTypeReference<>() {
    };

    private final SmartMessageConverter converter;
    private final EventIdCache eventIds;
    private final ConsumidorProperties properties;

    public void consumir(Message message, Channel channel, ProcesadorComando procesador) throws IOException {
        MessageProperties props = message.getMessageProperties();
        long deliveryTag = props.getDeliveryTag();
        MDC.put(MDC_CORRELATION_ID, props.getCorrelationId());
        try {
            boolean ok;
            try {
                CommandEnvelope envelope = deserializar(message);
                if (envelope.correlationId() != null) {
                    MDC.put(MDC_CORRELATION_ID, envelope.correlationId());
                }
                MDC.put(MDC_EVENT_ID, envelope.eventId());

                if (eventIds.yaProcesado(envelope.eventId())) {
                    log.warn("[{}] eventId={} duplicado (pedido {}): ACK sin reprocesar",
                            procesador.nombre(), envelope.eventId(), envelope.payload() == null ? null : envelope.payload().pedidoId());
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                procesarConReintentos(envelope, message, procesador);
                eventIds.registrar(envelope.eventId());
                ok = true;
            } catch (RuntimeException ex) {
                log.error("[{}] No se pudo procesar el mensaje de {} (routingKey={}): {}. NACK sin reencolar -> DLQ",
                        procesador.nombre(), props.getConsumerQueue(), props.getReceivedRoutingKey(), ex.getMessage(), ex);
                ok = false;
            }

            if (ok) {
                channel.basicAck(deliveryTag, false);
                log.info("[{}] ACK eventId={}", procesador.nombre(), MDC.get(MDC_EVENT_ID));
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_EVENT_ID);
        }
    }

    private CommandEnvelope deserializar(Message message) {
        Object convertido;
        try {
            convertido = converter.fromMessage(message, TIPO_ENVELOPE);
        } catch (MessageConversionException ex) {
            throw new MensajeInvalidoException("El cuerpo no es un sobre JSON válido: " + NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), ex);
        }
        // Con un content_type no JSON (p. ej. text/plain) el converter devuelve el cuerpo crudo.
        if (!(convertido instanceof CommandEnvelope envelope)) {
            throw new MensajeInvalidoException("El cuerpo no es un sobre JSON (content_type="
                    + message.getMessageProperties().getContentType() + ")");
        }
        if (envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new MensajeInvalidoException("El sobre no trae eventId");
        }
        return envelope;
    }

    private void procesarConReintentos(CommandEnvelope envelope, Message message, ProcesadorComando procesador) {
        int maxIntentos = Math.max(1, properties.maxIntentos());
        for (int intento = 1; ; intento++) {
            try {
                if (simularError(message)) {
                    throw new ErrorSimuladoException("Error simulado por el header " + HEADER_SIMULAR_ERROR + "=true");
                }
                procesador.procesar(envelope);
                return;
            } catch (MensajeInvalidoException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                if (intento >= maxIntentos) {
                    throw ex;
                }
                log.warn("[{}] Intento {}/{} falló: {}. Reintentando en {} ms", procesador.nombre(), intento,
                        maxIntentos, ex.getMessage(), properties.esperaReintento().toMillis());
                esperar();
            }
        }
    }

    private void esperar() {
        try {
            Thread.sleep(properties.esperaReintento());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento interrumpido", ex);
        }
    }

    static boolean simularError(Message message) {
        Object valor = message.getMessageProperties().getHeaders().get(HEADER_SIMULAR_ERROR);
        return valor != null && Boolean.parseBoolean(valor.toString().trim());
    }
}
