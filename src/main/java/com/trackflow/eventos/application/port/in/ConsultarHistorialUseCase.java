package com.trackflow.eventos.application.port.in;

import com.trackflow.eventos.domain.model.HistorialEnvio;

public interface ConsultarHistorialUseCase {

    HistorialEnvio consultar(String numeroSeguimiento);
}
