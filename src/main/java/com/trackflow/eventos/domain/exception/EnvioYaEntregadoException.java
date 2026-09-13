package com.trackflow.eventos.domain.exception;

import com.trackflow.eventos.domain.model.NumeroSeguimiento;

public class EnvioYaEntregadoException extends DominioException {

    public EnvioYaEntregadoException(NumeroSeguimiento numeroSeguimiento) {
        super("El envio " + numeroSeguimiento.valor()
                + " ya fue entregado y no admite nuevos eventos");
    }
}
