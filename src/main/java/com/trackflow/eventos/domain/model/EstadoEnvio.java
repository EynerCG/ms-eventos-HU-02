package com.trackflow.eventos.domain.model;

public enum EstadoEnvio {

    REGISTRADO,
    EN_TRANSITO,
    EN_CENTRO_DISTRIBUCION,
    EN_REPARTO,
    ENTREGA_FALLIDA,
    ENTREGADO;

    public boolean esTerminal() {
        return this == ENTREGADO;
    }
}
