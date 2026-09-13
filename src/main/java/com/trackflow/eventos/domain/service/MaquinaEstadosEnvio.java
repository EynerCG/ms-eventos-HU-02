package com.trackflow.eventos.domain.service;

import com.trackflow.eventos.domain.exception.TransicionNoPermitidaException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de transiciones validas entre estados de un envio.
 * Agregar un tipo de evento nuevo solo exige una entrada mas en este mapa (OCP).
 */
public class MaquinaEstadosEnvio {

    private record Transicion(EstadoEnvio estadoResultante, Set<EstadoEnvio> origenesPermitidos) {
    }

    private static final Map<TipoEvento, Transicion> TRANSICIONES = new EnumMap<>(TipoEvento.class);

    static {
        TRANSICIONES.put(TipoEvento.RECOGIDA, new Transicion(
                EstadoEnvio.EN_TRANSITO,
                EnumSet.of(EstadoEnvio.REGISTRADO)));

        TRANSICIONES.put(TipoEvento.LLEGADA_CENTRO, new Transicion(
                EstadoEnvio.EN_CENTRO_DISTRIBUCION,
                EnumSet.of(EstadoEnvio.EN_TRANSITO)));

        TRANSICIONES.put(TipoEvento.SALIDA_CENTRO, new Transicion(
                EstadoEnvio.EN_TRANSITO,
                EnumSet.of(EstadoEnvio.EN_CENTRO_DISTRIBUCION)));

        TRANSICIONES.put(TipoEvento.SALIDA_REPARTO, new Transicion(
                EstadoEnvio.EN_REPARTO,
                EnumSet.of(EstadoEnvio.EN_TRANSITO,
                        EstadoEnvio.EN_CENTRO_DISTRIBUCION,
                        EstadoEnvio.ENTREGA_FALLIDA)));

        TRANSICIONES.put(TipoEvento.INTENTO_ENTREGA_FALLIDO, new Transicion(
                EstadoEnvio.ENTREGA_FALLIDA,
                EnumSet.of(EstadoEnvio.EN_REPARTO)));

        TRANSICIONES.put(TipoEvento.ENTREGA, new Transicion(
                EstadoEnvio.ENTREGADO,
                EnumSet.of(EstadoEnvio.EN_REPARTO)));
    }

    public EstadoEnvio siguienteEstado(EstadoEnvio estadoActual, TipoEvento tipoEvento) {
        Transicion transicion = TRANSICIONES.get(tipoEvento);
        if (!transicion.origenesPermitidos().contains(estadoActual)) {
            throw new TransicionNoPermitidaException(estadoActual, tipoEvento);
        }
        return transicion.estadoResultante();
    }
}
