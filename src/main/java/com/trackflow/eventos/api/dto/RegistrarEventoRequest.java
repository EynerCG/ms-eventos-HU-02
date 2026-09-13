package com.trackflow.eventos.api.dto;

import com.trackflow.eventos.application.port.in.RegistrarEventoComando;
import com.trackflow.eventos.domain.model.TipoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RegistrarEventoRequest(

        @NotBlank(message = "El numero de seguimiento es obligatorio")
        String numeroSeguimiento,

        @NotNull(message = "El tipo de evento es obligatorio")
        TipoEvento tipoEvento,

        @NotNull(message = "El punto logistico es obligatorio")
        @Valid
        PuntoRequest punto,

        @NotNull(message = "La fecha en que ocurrio el evento es obligatoria")
        Instant ocurridoEn,

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
        String observaciones,

        @Size(max = 150, message = "El nombre de quien recibio no puede superar 150 caracteres")
        String recibidoPor) {

    public RegistrarEventoComando aComando() {
        return new RegistrarEventoComando(
                numeroSeguimiento,
                tipoEvento,
                punto.codigo(),
                punto.nombre(),
                punto.ciudad(),
                ocurridoEn,
                observaciones,
                recibidoPor);
    }
}
