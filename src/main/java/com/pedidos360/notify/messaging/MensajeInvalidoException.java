package com.pedidos360.notify.messaging;

/**
 * Error permanente: el mensaje no se puede deserializar o le faltan datos obligatorios.
 * No se reintenta (fallaría igual): va directo a NACK y a la DLQ.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
