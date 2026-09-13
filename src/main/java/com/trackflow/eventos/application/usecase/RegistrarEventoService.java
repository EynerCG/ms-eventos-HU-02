package com.trackflow.eventos.application.usecase;

import com.trackflow.eventos.application.port.in.RegistrarEventoComando;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.service.MaquinaEstadosEnvio;
import java.time.Clock;

public class RegistrarEventoService implements RegistrarEventoUseCase {

    private final EventoRepository eventoRepository;
    private final EnvioRastreadoRepository envioRastreadoRepository;
    private final EnvioEstadoPort envioEstadoPort;
    private final OutboxRepository outboxRepository;
    private final MaquinaEstadosEnvio maquinaEstados;
    private final Clock reloj;

    public RegistrarEventoService(EventoRepository eventoRepository,
                                  EnvioRastreadoRepository envioRastreadoRepository,
                                  EnvioEstadoPort envioEstadoPort,
                                  OutboxRepository outboxRepository,
                                  MaquinaEstadosEnvio maquinaEstados,
                                  Clock reloj) {
        this.eventoRepository = eventoRepository;
        this.envioRastreadoRepository = envioRastreadoRepository;
        this.envioEstadoPort = envioEstadoPort;
        this.outboxRepository = outboxRepository;
        this.maquinaEstados = maquinaEstados;
        this.reloj = reloj;
    }

    @Override
    public Evento registrar(RegistrarEventoComando comando) {
        NumeroSeguimiento numeroSeguimiento = new NumeroSeguimiento(comando.numeroSeguimiento());
        EnvioRastreado envio = obtenerEnvio(numeroSeguimiento);

        envio.verificarAdmiteEventos();
        EstadoEnvio nuevoEstado = maquinaEstados.siguienteEstado(
                envio.estadoActual(), comando.tipoEvento());
        envio.aplicar(nuevoEstado, comando.ocurridoEn());

        Evento evento = Evento.registrar(
                numeroSeguimiento,
                comando.tipoEvento(),
                nuevoEstado,
                new PuntoLogistico(comando.codigoPunto(), comando.nombrePunto(), comando.ciudadPunto()),
                comando.ocurridoEn(),
                reloj.instant(),
                comando.observaciones(),
                comando.recibidoPor());

        // El envio rastreado se guarda primero: el evento lo referencia por llave foranea.
        envioRastreadoRepository.guardar(envio);
        eventoRepository.guardar(evento);
        outboxRepository.encolar(MensajeOutbox.desde(evento));

        return evento;
    }

    /**
     * Busca el envio en la proyeccion local y, solo si no esta, lo hidrata desde ms-envios.
     * Asi el registro de eventos sigue funcionando aunque ms-envios este caido.
     */
    private EnvioRastreado obtenerEnvio(NumeroSeguimiento numeroSeguimiento) {
        return envioRastreadoRepository.buscar(numeroSeguimiento)
                .orElseGet(() -> hidratarDesdeMsEnvios(numeroSeguimiento));
    }

    private EnvioRastreado hidratarDesdeMsEnvios(NumeroSeguimiento numeroSeguimiento) {
        EstadoEnvio estadoRemoto = envioEstadoPort.consultarEstado(numeroSeguimiento)
                .orElseThrow(() -> new EnvioNoEncontradoException(numeroSeguimiento));
        return new EnvioRastreado(numeroSeguimiento, estadoRemoto, null);
    }
}
