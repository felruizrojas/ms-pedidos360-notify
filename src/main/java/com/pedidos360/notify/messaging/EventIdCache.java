package com.pedidos360.notify.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.pedidos360.notify.config.ConsumidorProperties;

/**
 * Idempotencia: recuerda los últimos N eventId procesados con éxito (LRU en memoria, N = cache-event-ids).
 * Sirve para una sola réplica; con varias réplicas de notify haría falta un almacén compartido
 * (Redis con TTL o una tabla con eventId único), porque cada instancia tiene su propio caché.
 */
@Component
public class EventIdCache {

    private final Set<String> ids;

    public EventIdCache(ConsumidorProperties properties) {
        int maximo = properties.cacheEventIds();
        this.ids = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > maximo;
            }
        }));
    }

    public boolean yaProcesado(String eventId) {
        return ids.contains(eventId);
    }

    public void registrar(String eventId) {
        ids.add(eventId);
    }

    public int tamano() {
        return ids.size();
    }
}
