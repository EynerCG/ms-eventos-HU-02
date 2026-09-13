package com.trackflow.eventos.application.port.out;

import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Unico punto de integracion con ms-envios. Se habla por su API publica,
 * nunca contra su base de datos.
 */
public interface EnvioEstadoPort {

    Optional<EstadoEnvio> consultarEstado(NumeroSeguimiento numeroSeguimiento);

    void actualizarEstado(NumeroSeguimiento numeroSeguimiento,
                          EstadoEnvio estado,
                          Instant ocurridoEn,
                          UUID idEvento);
}
