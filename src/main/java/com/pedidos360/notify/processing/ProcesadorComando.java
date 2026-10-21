package com.pedidos360.notify.processing;

import com.pedidos360.notify.messaging.dto.CommandEnvelope;

/** Procesa el comando de una cola. Si lanza una excepción, el mensaje se reintenta y luego va a la DLQ. */
public interface ProcesadorComando {

    /** Nombre corto para el log (email, kitchen, invoice). */
    String nombre();

    void procesar(CommandEnvelope envelope);
}
