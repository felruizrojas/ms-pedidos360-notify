package com.pedidos360.notify.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import com.pedidos360.notify.Ejemplos;

class BoletaPdfTest {

    @Test
    void generaUnPdfConLosDatosDeLaBoleta() throws Exception {
        byte[] pdf = new BoletaPdf().generar(Ejemplos.envelope("DESPACHADO", "ENTREGADO").payload());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String texto = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(texto).contains("BOLETA", "Pedido #15", "María Pérez", "maria@contoso.cl",
                "Pan Amasado", "$1.200", "Café Americano", "$1.500", "TOTAL", "$2.700");
    }
}
