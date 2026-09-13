package com.trackflow.eventos.domain.model;

public enum TipoEvento {

    RECOGIDA,
    LLEGADA_CENTRO,
    SALIDA_CENTRO,
    SALIDA_REPARTO,
    INTENTO_ENTREGA_FALLIDO,
    ENTREGA;

    public boolean esEntrega() {
        return this == ENTREGA;
    }
}
