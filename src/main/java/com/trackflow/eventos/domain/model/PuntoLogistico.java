package com.trackflow.eventos.domain.model;

import com.trackflow.eventos.domain.exception.DatoInvalidoException;
import java.util.Locale;

public record PuntoLogistico(String codigo, String nombre, String ciudad) {

    public PuntoLogistico {
        if (codigo == null || codigo.isBlank()) {
            throw new DatoInvalidoException("El codigo del punto logistico es obligatorio");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new DatoInvalidoException("El nombre del punto logistico es obligatorio");
        }
        codigo = codigo.trim().toUpperCase(Locale.ROOT);
        nombre = nombre.trim();
        ciudad = ciudad == null ? null : ciudad.trim();
    }
}
