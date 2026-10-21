package com.pedidos360.notify.processing;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

import lombok.extern.slf4j.Slf4j;

/** invoice.gen: registra en el log la boleta del pedido entregado (ítems, subtotales y total). */
@Slf4j
@Component
public class InvoiceProcessor implements ProcesadorComando {

    private static final String LINEA = "-".repeat(56);

    @Override
    public String nombre() {
        return "invoice";
    }

    @Override
    public void procesar(CommandEnvelope envelope) {
        PedidoComandoPayload p = Formato.payloadConItems(envelope);
        BigDecimal suma = p.items().stream()
                .map(PedidoComandoPayload.Item::subtotal)
                .filter(s -> s != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (p.total() != null && suma.compareTo(p.total()) != 0) {
            log.warn("La suma de subtotales ({}) no coincide con el total ({}) del pedido {}",
                    suma, p.total(), p.pedidoId());
        }
        log.info("Boleta generada:\n{}", construir(envelope));
    }

    public String construir(CommandEnvelope envelope) {
        PedidoComandoPayload p = Formato.payloadConItems(envelope);
        StringBuilder b = new StringBuilder()
                .append("======================== BOLETA ========================\n")
                .append("Pedido #").append(p.pedidoId()).append("   Fecha: ").append(Formato.fecha(p.fecha())).append('\n')
                .append("Cliente: ").append(p.clienteNombre()).append(" <").append(p.clienteEmail()).append(">\n")
                .append(LINEA).append('\n')
                .append(String.format("%4s  %-24s %11s %11s%n", "Cant", "Producto", "P. unit.", "Subtotal"));
        for (PedidoComandoPayload.Item i : p.items()) {
            b.append(String.format("%4d  %-24s %11s %11s%n", i.cantidad(), recortar(i.productoNombre()),
                    Formato.pesos(i.precioUnitario()), Formato.pesos(i.subtotal())));
        }
        return b.append(LINEA).append('\n')
                .append(String.format("%-42s %11s%n", "TOTAL", Formato.pesos(p.total())))
                .append("========================================================")
                .toString();
    }

    private static String recortar(String nombre) {
        if (nombre == null) {
            return "";
        }
        return nombre.length() > 24 ? nombre.substring(0, 23) + "…" : nombre;
    }
}
