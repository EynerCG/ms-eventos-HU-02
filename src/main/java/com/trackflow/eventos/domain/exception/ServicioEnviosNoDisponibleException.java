package com.trackflow.eventos.domain.exception;

public class ServicioEnviosNoDisponibleException extends DominioException {

    public ServicioEnviosNoDisponibleException(String detalle) {
        super("No fue posible consultar ms-envios: " + detalle);
    }
}
