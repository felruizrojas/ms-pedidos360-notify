package com.pedidos360.notify.processing;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.pedidos360.notify.messaging.dto.CommandEnvelope;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

import lombok.extern.slf4j.Slf4j;

/** kitchen.ticket: registra en el log el ticket de cocina (pedido, ítems y cantidades). */
@Slf4j
@Component
public class KitchenTicketProcessor implements ProcesadorComando {

    private static final String LINEA = "-".repeat(44);

    @Override
    public String nombre() {
        return "kitchen";
    }

    @Override
    public Set<String> tiposAceptados() {
        return Set.of("KitchenTicketRequested");
    }

    @Override
    public void procesar(CommandEnvelope envelope) {
        log.info("Ticket de cocina generado:\n{}", construir(envelope));
    }

    public String construir(CommandEnvelope envelope) {
        PedidoComandoPayload p = Formato.payload(envelope);
        StringBuilder t = new StringBuilder()
                .append("=============== TICKET DE COCINA ===============\n")
                .append("Pedido #").append(p.pedidoId()).append("   Aceptado: ").append(Formato.fecha(p.fecha())).append('\n')
                .append("Cliente: ").append(p.clienteNombre()).append('\n')
                .append(LINEA).append('\n');
        int unidades = 0;
        for (PedidoComandoPayload.Item i : p.items()) {
            int cantidad = i.cantidad() == null ? 0 : i.cantidad();
            unidades += cantidad;
            t.append(String.format("%4d x %s%n", cantidad, i.productoNombre()));
        }
        return t.append(LINEA).append('\n')
                .append("Total de unidades: ").append(unidades).append('\n')
                .append("================================================")
                .toString();
    }
}
