package com.trackflow.eventos.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record HistorialEnvio(
        NumeroSeguimiento numeroSeguimiento,
        EstadoEnvio estadoActual,
        List<Evento> eventos) {

    public HistorialEnvio {
        eventos = eventos.stream()
                .sorted(Comparator.comparing(Evento::ocurridoEn))
                .toList();
    }

    public Optional<Evento> ultimoMovimiento() {
        return eventos.isEmpty()
                ? Optional.empty()
                : Optional.of(eventos.get(eventos.size() - 1));
    }
}
