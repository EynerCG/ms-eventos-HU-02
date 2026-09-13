package com.trackflow.eventos.application.port.out;

import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.util.List;

public interface EventoRepository {

    void guardar(Evento evento);

    List<Evento> buscarPorNumeroSeguimiento(NumeroSeguimiento numeroSeguimiento);
}
