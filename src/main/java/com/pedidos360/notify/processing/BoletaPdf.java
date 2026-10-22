package com.pedidos360.notify.processing;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pedidos360.notify.messaging.dto.PedidoComandoPayload;

/** Boleta del pedido en PDF (mismos datos que la boleta del log), para adjuntarla al correo de invoice.gen. */
@Component
public class BoletaPdf {

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Color GRIS = new Color(235, 235, 235);

    public byte[] generar(PedidoComandoPayload p) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        Paragraph titulo = new Paragraph("BOLETA", TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(16);
        documento.add(titulo);
        documento.add(new Paragraph("Pedido #" + p.pedidoId() + "    Fecha: " + Formato.fecha(p.fecha()), NORMAL));
        documento.add(new Paragraph("Cliente: " + p.clienteNombre() + " <" + p.clienteEmail() + ">", NORMAL));

        PdfPTable tabla = new PdfPTable(new float[] {1, 5, 2, 2});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(16);
        for (String encabezado : new String[] {"Cant.", "Producto", "P. unit.", "Subtotal"}) {
            PdfPCell celda = celda(encabezado, NEGRITA, encabezado.equals("Producto") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            celda.setBackgroundColor(GRIS);
            tabla.addCell(celda);
        }
        for (PedidoComandoPayload.Item i : p.items()) {
            tabla.addCell(celda(String.valueOf(i.cantidad()), NORMAL, Element.ALIGN_RIGHT));
            tabla.addCell(celda(i.productoNombre() == null ? "" : i.productoNombre(), NORMAL, Element.ALIGN_LEFT));
            tabla.addCell(celda(Formato.pesos(i.precioUnitario()), NORMAL, Element.ALIGN_RIGHT));
            tabla.addCell(celda(Formato.pesos(i.subtotal()), NORMAL, Element.ALIGN_RIGHT));
        }
        PdfPCell total = celda("TOTAL", NEGRITA, Element.ALIGN_RIGHT);
        total.setColspan(3);
        tabla.addCell(total);
        tabla.addCell(celda(Formato.pesos(p.total()), NEGRITA, Element.ALIGN_RIGHT));
        documento.add(tabla);

        documento.close();
        return salida.toByteArray();
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(6);
        celda.setBorder(Rectangle.BOTTOM);
        return celda;
    }
}
