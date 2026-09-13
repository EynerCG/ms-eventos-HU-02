package com.trackflow.eventos.domain.model;

import com.trackflow.eventos.domain.exception.DatoInvalidoException;
import java.time.Instant;
import java.util.UUID;

public record Evento(
        UUID id,
        NumeroSeguimiento numeroSeguimiento,
        TipoEvento tipo,
        EstadoEnvio estadoResultante,
        PuntoLogistico punto,
        Instant ocurridoEn,
        Instant registradoEn,
        String observaciones,
        String recibidoPor) {

    public Evento {
        if (id == null || numeroSeguimiento == null || tipo == null
                || estadoResultante == null || punto == null
                || ocurridoEn == null || registradoEn == null) {
            throw new DatoInvalidoException("El evento tiene datos obligatorios sin diligenciar");
        }
        if (!tipo.esEntrega()) {
            recibidoPor = null;
        }
    }

    public static Evento registrar(NumeroSeguimiento numeroSeguimiento,
                                   TipoEvento tipo,
                                   EstadoEnvio estadoResultante,
                                   PuntoLogistico punto,
                                   Instant ocurridoEn,
                                   Instant registradoEn,
                                   String observaciones,
                                   String recibidoPor) {
        return new Evento(UUID.randomUUID(), numeroSeguimiento, tipo, estadoResultante,
                punto, ocurridoEn, registradoEn, observaciones, recibidoPor);
    }
}
