package com.trackflow.eventos.domain.model;

import com.trackflow.eventos.domain.exception.DatoInvalidoException;
import java.util.Locale;
import java.util.regex.Pattern;

public record NumeroSeguimiento(String valor) {

    private static final Pattern PATRON = Pattern.compile("^[A-Z0-9-]{6,30}$");

    public NumeroSeguimiento {
        if (valor == null || valor.isBlank()) {
            throw new DatoInvalidoException("El numero de seguimiento es obligatorio");
        }
        valor = valor.trim().toUpperCase(Locale.ROOT);
        if (!PATRON.matcher(valor).matches()) {
            throw new DatoInvalidoException(
                    "El numero de seguimiento '" + valor + "' no tiene un formato valido");
        }
    }

    @Override
    public String toString() {
        return valor;
    }
}
