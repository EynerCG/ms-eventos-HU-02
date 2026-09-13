package com.trackflow.eventos.application.port.in;

import com.trackflow.eventos.domain.model.TipoEvento;
import java.time.Instant;

public record RegistrarEventoComando(
        String numeroSeguimiento,
        TipoEvento tipoEvento,
        String codigoPunto,
        String nombrePunto,
        String ciudadPunto,
        Instant ocurridoEn,
        String observaciones,
        String recibidoPor) {
}
