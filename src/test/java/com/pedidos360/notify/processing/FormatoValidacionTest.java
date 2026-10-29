package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pedidos360.notify.Ejemplos;
import com.pedidos360.notify.messaging.MensajeInvalidoException;
import com.pedidos360.notify.messaging.dto.CommandEnvelope;

/** Información errónea en el payload: MensajeInvalidoException (NACK sin reintentos -> DLQ). */
class FormatoValidacionTest {

    private static CommandEnvelope con(String buscar, String reemplazo) {
        String json = Ejemplos.ENVELOPE_JSON;
        assertThat(json).contains(buscar);
        return Ejemplos.JSON.readValue(json.replace(buscar, reemplazo), CommandEnvelope.class);
    }

    @Test
    void elEnvelopeDeEjemploEsValido() {
        assertThat(Formato.payload(Ejemplos.envelope()).pedidoId()).isEqualTo(15L);
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource(delimiter = '|', textBlock = """
            "estadoNuevo": "ACEPTADO"            | "estadoNuevo": "PERDIDO"            | estado desconocido
            "usuarioRol": "Cliente"              | "usuarioRol": "Hacker"              | rol que no existe en el sistema
            "usuarioRol": "Cliente"              | "usuarioRol": "cliente"             | rol con otra capitalización
            "usuarioRol": "Cliente",             | "usuarioRol": null,                 | sin rol
            "clienteEmail": "maria@contoso.cl"   | "clienteEmail": "maria-contoso"     | email sin @
            "total": 2700.00                     | "total": 9999.00                    | total distinto de la suma
            "cantidad": 2, "precioUnitario": 600.00, "subtotal": 1200.00 | "cantidad": 2, "precioUnitario": 600.00, "subtotal": 1300.00 | subtotal distinto de precio x cantidad
            "cantidad": 2,                       | "cantidad": 0,                      | cantidad cero
            "cantidad": 2,                       | "cantidad": -2,                     | cantidad negativa
            "precioUnitario": 600.00, "subtotal": 1200.00 | "subtotal": 1200.00        | ítem sin precio
            """)
    void informacionErroneaVaALaDlq(String buscar, String reemplazo, String caso) {
        CommandEnvelope envelope = con(buscar, reemplazo);
        assertThatThrownBy(() -> Formato.payload(envelope)).as(caso).isInstanceOf(MensajeInvalidoException.class);
    }

    @Test
    void pedidoSinItemsVaALaDlq() {
        String json = Ejemplos.ENVELOPE_JSON.replaceAll("(?s)\"items\": \\[.*?\\],", "\"items\": [],");
        CommandEnvelope envelope = Ejemplos.JSON.readValue(json, CommandEnvelope.class);
        assertThatThrownBy(() -> Formato.payload(envelope)).isInstanceOf(MensajeInvalidoException.class)
                .hasMessageContaining("no trae items");
    }
}
