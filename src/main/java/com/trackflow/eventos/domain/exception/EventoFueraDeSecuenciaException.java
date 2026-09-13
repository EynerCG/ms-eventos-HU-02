package com.trackflow.eventos.domain.exception;

import java.time.Instant;

public class EventoFueraDeSecuenciaException extends DominioException {

    public EventoFueraDeSecuenciaException(Instant ocurridoEn, Instant ultimoEventoEn) {
        super("El evento ocurrio el " + ocurridoEn
                + ", que es anterior al ultimo evento registrado el " + ultimoEventoEn);
    }
}
