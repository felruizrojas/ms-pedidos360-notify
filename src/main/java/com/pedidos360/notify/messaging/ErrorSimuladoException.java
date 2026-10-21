package com.pedidos360.notify.messaging;

/** Lanzada a propósito cuando el mensaje trae el header x-simular-error=true (demo de las DLQ). */
public class ErrorSimuladoException extends RuntimeException {

    public ErrorSimuladoException(String mensaje) {
        super(mensaje);
    }
}
