package com.trackflow.eventos.application.port.out;

import com.trackflow.eventos.domain.model.Evento;
import java.time.Instant;
import java.util.UUID;

public record MensajeOutbox(
        UUID idEvento,
        String numeroSeguimiento,
        String estado,
        Instant ocurridoEn,
        int intentos) {

    public static MensajeOutbox desde(Evento evento) {
        return new MensajeOutbox(
                evento.id(),
                evento.numeroSeguimiento().valor(),
                evento.estadoResultante().name(),
                evento.ocurridoEn(),
                0);
    }
}
