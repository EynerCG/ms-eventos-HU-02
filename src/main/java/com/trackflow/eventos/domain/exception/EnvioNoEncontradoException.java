package com.trackflow.eventos.domain.exception;

import com.trackflow.eventos.domain.model.NumeroSeguimiento;

public class EnvioNoEncontradoException extends DominioException {

    public EnvioNoEncontradoException(NumeroSeguimiento numeroSeguimiento) {
        super("No existe un envio con el numero de seguimiento " + numeroSeguimiento.valor());
    }
}
