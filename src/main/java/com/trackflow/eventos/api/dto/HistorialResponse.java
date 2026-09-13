package com.trackflow.eventos.api.dto;

import com.trackflow.eventos.domain.model.HistorialEnvio;
import java.util.List;

public record HistorialResponse(
        String numeroSeguimiento,
        String estadoActual,
        List<EventoResponse> eventos) {

    public static HistorialResponse desde(HistorialEnvio historial) {
        return new HistorialResponse(
                historial.numeroSeguimiento().valor(),
                historial.estadoActual().name(),
                historial.eventos().stream().map(EventoResponse::desde).toList());
    }
}
