package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.pedidos360.notify.Ejemplos;

class KitchenTicketProcessorTest {

    @Test
    void armaElTicketDelEnvelopeDeEjemplo() {
        String ticket = new KitchenTicketProcessor().construir(Ejemplos.envelope());

        assertThat(ticket)
                .contains("TICKET DE COCINA", "Pedido #15", "Aceptado: 20-10-2026 12:10", "Cliente: María Pérez",
                        "   2 x Pan Amasado", "   1 x Café Americano", "Total de unidades: 3")
                .doesNotContain("$");
    }
}
