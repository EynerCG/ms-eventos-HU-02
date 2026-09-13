package com.trackflow.eventos.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PuntoRequest(

        @NotBlank(message = "El codigo del punto es obligatorio")
        String codigo,

        @NotBlank(message = "El nombre del punto es obligatorio")
        String nombre,

        String ciudad) {
}
