package com.pedidos360.notify.processing;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.pedidos360.notify.messaging.MensajeInvalidoException;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

/** Formatos comunes de los textos (pesos chilenos, hora de Santiago) y validaciones del payload. */
final class Formato {

    private static final Locale ES_CL = Locale.of("es", "CL");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.of("America/Santiago"));

    private Formato() {
    }

    static String pesos(BigDecimal monto) {
        if (monto == null) {
            return "-";
        }
        NumberFormat nf = NumberFormat.getIntegerInstance(ES_CL);
        nf.setGroupingUsed(true);
        return "$" + nf.format(monto);
    }

    static String fecha(Instant instante) {
        return instante == null ? "-" : FECHA.format(instante);
    }

    static PedidoComandoPayload payload(CommandEnvelope envelope) {
        PedidoComandoPayload p = envelope.payload();
        if (p == null || p.pedidoId() == null) {
            throw new MensajeInvalidoException("El mensaje no trae payload.pedidoId");
        }
        return p;
    }

    static PedidoComandoPayload payloadConItems(CommandEnvelope envelope) {
        PedidoComandoPayload p = payload(envelope);
        if (p.items() == null || p.items().isEmpty()) {
            throw new MensajeInvalidoException("El pedido " + p.pedidoId() + " no trae items");
        }
        return p;
    }
}
