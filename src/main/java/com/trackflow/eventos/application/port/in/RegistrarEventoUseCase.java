package com.trackflow.eventos.application.port.in;

import com.trackflow.eventos.domain.model.Evento;

public interface RegistrarEventoUseCase {

    Evento registrar(RegistrarEventoComando comando);
}
