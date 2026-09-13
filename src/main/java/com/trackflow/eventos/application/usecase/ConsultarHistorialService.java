package com.trackflow.eventos.application.usecase;

import com.trackflow.eventos.application.port.in.ConsultarHistorialUseCase;
import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.HistorialEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;

public class ConsultarHistorialService implements ConsultarHistorialUseCase {

    private final EventoRepository eventoRepository;
    private final EnvioRastreadoRepository envioRastreadoRepository;

    public ConsultarHistorialService(EventoRepository eventoRepository,
                                     EnvioRastreadoRepository envioRastreadoRepository) {
        this.eventoRepository = eventoRepository;
        this.envioRastreadoRepository = envioRastreadoRepository;
    }

    @Override
    public HistorialEnvio consultar(String numeroSeguimiento) {
        NumeroSeguimiento numero = new NumeroSeguimiento(numeroSeguimiento);
        EnvioRastreado envio = envioRastreadoRepository.buscar(numero)
                .orElseThrow(() -> new EnvioNoEncontradoException(numero));

        return new HistorialEnvio(
                numero,
                envio.estadoActual(),
                eventoRepository.buscarPorNumeroSeguimiento(numero));
    }
}
