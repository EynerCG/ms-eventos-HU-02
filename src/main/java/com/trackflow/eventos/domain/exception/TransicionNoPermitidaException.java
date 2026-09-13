package com.trackflow.eventos.domain.exception;

import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.TipoEvento;

public class TransicionNoPermitidaException extends DominioException {

    public TransicionNoPermitidaException(EstadoEnvio estadoActual, TipoEvento tipoEvento) {
        super("No se puede registrar un evento " + tipoEvento
                + " sobre un envio en estado " + estadoActual);
    }
}
