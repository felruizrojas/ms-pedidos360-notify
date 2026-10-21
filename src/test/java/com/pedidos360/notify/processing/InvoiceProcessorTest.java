package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.pedidos360.notify.Ejemplos;
import com.pedidos360.notify.messaging.MensajeInvalidoException;

class InvoiceProcessorTest {

    @Test
    void armaLaBoletaDelEnvelopeDeEjemplo() {
        String boleta = new InvoiceProcessor().construir(Ejemplos.envelope("DESPACHADO", "ENTREGADO"));

        assertThat(boleta)
                .contains("BOLETA", "Pedido #15", "Cliente: María Pérez <maria@contoso.cl>")
                .containsPattern("2  Pan Amasado\\s+\\$600\\s+\\$1\\.200")
                .containsPattern("1  Café Americano\\s+\\$1\\.500\\s+\\$1\\.500")
                .containsPattern("TOTAL\\s+\\$2\\.700");
    }

    @Test
    void sinItemsEsMensajeInvalido() {
        String json = Ejemplos.ENVELOPE_JSON.replaceAll("(?s)\"items\": \\[.*?\\]", "\"items\": []");
        var envelope = Ejemplos.JSON.readValue(json, com.pedidos360.notify.messaging.dto.CommandEnvelope.class);

        assertThatThrownBy(() -> new InvoiceProcessor().construir(envelope)).isInstanceOf(MensajeInvalidoException.class);
    }
}
