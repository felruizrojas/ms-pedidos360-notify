package com.pedidos360.notify.processing;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.pedidos360.notify.messaging.MensajeInvalidoException;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

/**
 * Formatos comunes de los textos (pesos chilenos, hora de Santiago) y validaciones del payload. Un payload con
 * información errónea lanza MensajeInvalidoException: NACK sin reintentos y RabbitMQ lo deja en la DLQ.
 */
final class Formato {

    private static final Locale ES_CL = Locale.of("es", "CL");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.of("America/Santiago"));
    private static final Set<String> ESTADOS = Set.of("CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO",
            "ENTREGADO", "CANCELADO");
    /** Los tres roles de la App registration de Entra ID. */
    private static final Set<String> ROLES = Set.of("Admin", "Operador", "Cliente");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

    /**
     * Valida lo que todo comando de pedido debe traer bien: pedidoId, estado conocido, rol del usuario que hizo la
     * acción (Admin, Operador o Cliente), email con formato válido (si viene), ítems con cantidad y precio,
     * subtotal = precio x cantidad y total = suma de subtotales.
     */
    static PedidoComandoPayload payload(CommandEnvelope envelope) {
        PedidoComandoPayload p = envelope.payload();
        if (p == null || p.pedidoId() == null) {
            throw new MensajeInvalidoException("El mensaje no trae payload.pedidoId");
        }
        Long id = p.pedidoId();
        if (p.estadoNuevo() == null || !ESTADOS.contains(p.estadoNuevo())) {
            throw new MensajeInvalidoException("El pedido " + id + " trae un estadoNuevo desconocido: " + p.estadoNuevo());
        }
        if (p.usuarioRol() == null || !ROLES.contains(p.usuarioRol())) {
            throw new MensajeInvalidoException("El pedido " + id + " trae un usuarioRol desconocido: " + p.usuarioRol()
                    + " (acepta " + ROLES + ")");
        }
        if (p.clienteEmail() != null && !p.clienteEmail().isBlank() && !EMAIL.matcher(p.clienteEmail()).matches()) {
            throw new MensajeInvalidoException("El pedido " + id + " trae un clienteEmail inválido: " + p.clienteEmail());
        }
        if (p.items() == null || p.items().isEmpty()) {
            throw new MensajeInvalidoException("El pedido " + id + " no trae items");
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (PedidoComandoPayload.Item i : p.items()) {
            if (i.cantidad() == null || i.cantidad() <= 0 || i.precioUnitario() == null
                    || i.precioUnitario().signum() < 0 || i.subtotal() == null) {
                throw new MensajeInvalidoException("El pedido " + id + " trae un ítem sin cantidad, precio o subtotal válido: "
                        + i.productoNombre());
            }
            if (i.precioUnitario().multiply(BigDecimal.valueOf(i.cantidad())).compareTo(i.subtotal()) != 0) {
                throw new MensajeInvalidoException("El pedido " + id + " trae un subtotal que no es precio x cantidad: "
                        + i.productoNombre());
            }
            suma = suma.add(i.subtotal());
        }
        if (p.total() == null || suma.compareTo(p.total()) != 0) {
            throw new MensajeInvalidoException("El total del pedido " + id + " (" + p.total()
                    + ") no coincide con la suma de los subtotales (" + suma + ")");
        }
        return p;
    }

    /** Para los comandos que se envían al cliente (email e invoice): sin destinatario no hay a quién enviarlo. */
    static void exigirEmail(PedidoComandoPayload p) {
        if (p.clienteEmail() == null || p.clienteEmail().isBlank()) {
            throw new MensajeInvalidoException("El pedido " + p.pedidoId() + " no trae clienteEmail");
        }
    }
}
