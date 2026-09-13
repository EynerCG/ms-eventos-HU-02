package com.trackflow.eventos.application.port.out;

import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.util.Optional;

public interface EnvioRastreadoRepository {

    Optional<EnvioRastreado> buscar(NumeroSeguimiento numeroSeguimiento);

    void guardar(EnvioRastreado envio);
}
