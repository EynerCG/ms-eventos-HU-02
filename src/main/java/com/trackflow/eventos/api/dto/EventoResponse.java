package com.trackflow.eventos.api.dto;

import com.trackflow.eventos.domain.model.Evento;
import java.time.Instant;
import java.util.UUID;

public record EventoResponse(
        UUID idEvento,
        String numeroSeguimiento,
        String tipoEvento,
        String estadoResultante,
        PuntoRequest punto,
        Instant ocurridoEn,
        Instant registradoEn,
        String observaciones,
        String recibidoPor) {

    public static EventoResponse desde(Evento evento) {
        return new EventoResponse(
                evento.id(),
                evento.numeroSeguimiento().valor(),
                evento.tipo().name(),
                evento.estadoResultante().name(),
                new PuntoRequest(evento.punto().codigo(), evento.punto().nombre(),
                        evento.punto().ciudad()),
                evento.ocurridoEn(),
                evento.registradoEn(),
                evento.observaciones(),
                evento.recibidoPor());
    }
}
