package com.trackflow.eventos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.HistorialEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultarHistorialServiceTest {

    private static final NumeroSeguimiento NUMERO = new NumeroSeguimiento("TRK-0001");

    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private EnvioRastreadoRepository envioRastreadoRepository;

    @Test
    @DisplayName("Devuelve el estado actual y el historial en orden cronologico")
    void devuelveHistorialOrdenado() {
        when(envioRastreadoRepository.buscar(NUMERO)).thenReturn(Optional.of(
                new EnvioRastreado(NUMERO, EstadoEnvio.ENTREGADO, null)));

        Evento entrega = evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO,
                Instant.parse("2026-09-13T10:00:00Z"));
        Evento recogida = evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO,
                Instant.parse("2026-09-11T10:00:00Z"));
        when(eventoRepository.buscarPorNumeroSeguimiento(NUMERO))
                .thenReturn(List.of(entrega, recogida));

        HistorialEnvio historial = servicio().consultar("trk-0001");

        assertThat(historial.estadoActual()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(historial.eventos()).containsExactly(recogida, entrega);
        assertThat(historial.ultimoMovimiento()).contains(entrega);
    }

    @Test
    @DisplayName("Informa que el envio no existe cuando no esta en la proyeccion local")
    void informaEnvioInexistente() {
        when(envioRastreadoRepository.buscar(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio().consultar("TRK-9999"))
                .isInstanceOf(EnvioNoEncontradoException.class);
    }

    private ConsultarHistorialService servicio() {
        return new ConsultarHistorialService(eventoRepository, envioRastreadoRepository);
    }

    private static Evento evento(TipoEvento tipo, EstadoEnvio estado, Instant ocurridoEn) {
        return Evento.registrar(NUMERO, tipo, estado,
                new PuntoLogistico("CD-MDE", "Centro Medellin", "Medellin"),
                ocurridoEn, Instant.parse("2026-09-13T11:00:00Z"), null, null);
    }
}
