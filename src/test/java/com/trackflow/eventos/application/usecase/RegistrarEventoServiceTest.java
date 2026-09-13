package com.trackflow.eventos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.trackflow.eventos.application.port.in.RegistrarEventoComando;
import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.exception.EnvioYaEntregadoException;
import com.trackflow.eventos.domain.exception.EventoFueraDeSecuenciaException;
import com.trackflow.eventos.domain.exception.TransicionNoPermitidaException;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.TipoEvento;
import com.trackflow.eventos.domain.service.MaquinaEstadosEnvio;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarEventoServiceTest {

    private static final String NUMERO = "TRK-0001";
    private static final Instant OCURRIDO_EN = Instant.parse("2026-09-13T10:00:00Z");
    private static final Instant AHORA = Instant.parse("2026-09-13T11:00:00Z");

    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private EnvioRastreadoRepository envioRastreadoRepository;
    @Mock
    private EnvioEstadoPort envioEstadoPort;
    @Mock
    private OutboxRepository outboxRepository;

    private RegistrarEventoService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new RegistrarEventoService(
                eventoRepository, envioRastreadoRepository, envioEstadoPort, outboxRepository,
                new MaquinaEstadosEnvio(), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("AC1: el evento queda en el historial y el envio pasa al estado que corresponde")
    void registraEventoYActualizaEstado() {
        envioLocalEn(EstadoEnvio.REGISTRADO, null);

        Evento evento = servicio.registrar(comando(TipoEvento.RECOGIDA, null));

        assertThat(evento.estadoResultante()).isEqualTo(EstadoEnvio.EN_TRANSITO);
        assertThat(evento.numeroSeguimiento().valor()).isEqualTo(NUMERO);
        assertThat(evento.ocurridoEn()).isEqualTo(OCURRIDO_EN);
        assertThat(evento.registradoEn()).isEqualTo(AHORA);
        assertThat(evento.punto().codigo()).isEqualTo("CD-MDE");

        verify(eventoRepository).guardar(evento);

        ArgumentCaptor<EnvioRastreado> envioGuardado = ArgumentCaptor.forClass(EnvioRastreado.class);
        verify(envioRastreadoRepository).guardar(envioGuardado.capture());
        assertThat(envioGuardado.getValue().estadoActual()).isEqualTo(EstadoEnvio.EN_TRANSITO);
        assertThat(envioGuardado.getValue().ultimoEventoEn()).isEqualTo(OCURRIDO_EN);
    }

    @Test
    @DisplayName("AC2: la entrega deja el envio ENTREGADO y guarda quien recibio")
    void registraEntrega() {
        envioLocalEn(EstadoEnvio.EN_REPARTO, Instant.parse("2026-09-13T08:00:00Z"));

        Evento evento = servicio.registrar(comando(TipoEvento.ENTREGA, "Ana Perez"));

        assertThat(evento.estadoResultante()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(evento.recibidoPor()).isEqualTo("Ana Perez");
    }

    @Test
    @DisplayName("AC3: si el envio no existe ni local ni en ms-envios, no se registra nada")
    void rechazaEnvioInexistente() {
        when(envioRastreadoRepository.buscar(any())).thenReturn(Optional.empty());
        when(envioEstadoPort.consultarEstado(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(comando(TipoEvento.RECOGIDA, null)))
                .isInstanceOf(EnvioNoEncontradoException.class)
                .hasMessageContaining(NUMERO);

        verifyNoInteractions(eventoRepository, outboxRepository);
        verify(envioRastreadoRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Hidrata el envio desde ms-envios cuando aun no esta en la proyeccion local")
    void hidrataDesdeMsEnvios() {
        when(envioRastreadoRepository.buscar(any())).thenReturn(Optional.empty());
        when(envioEstadoPort.consultarEstado(new NumeroSeguimiento(NUMERO)))
                .thenReturn(Optional.of(EstadoEnvio.REGISTRADO));

        Evento evento = servicio.registrar(comando(TipoEvento.RECOGIDA, null));

        assertThat(evento.estadoResultante()).isEqualTo(EstadoEnvio.EN_TRANSITO);
        verify(eventoRepository).guardar(evento);
    }

    @Test
    @DisplayName("Encola el cambio de estado en el outbox para sincronizarlo con ms-envios")
    void encolaEnOutbox() {
        envioLocalEn(EstadoEnvio.EN_REPARTO, null);

        Evento evento = servicio.registrar(comando(TipoEvento.ENTREGA, "Ana"));

        ArgumentCaptor<MensajeOutbox> mensaje = ArgumentCaptor.forClass(MensajeOutbox.class);
        verify(outboxRepository).encolar(mensaje.capture());
        assertThat(mensaje.getValue().idEvento()).isEqualTo(evento.id());
        assertThat(mensaje.getValue().estado()).isEqualTo("ENTREGADO");
        assertThat(mensaje.getValue().intentos()).isZero();
    }

    @Test
    @DisplayName("Rechaza un evento sobre un envio ya entregado")
    void rechazaEventoSobreEnvioEntregado() {
        envioLocalEn(EstadoEnvio.ENTREGADO, OCURRIDO_EN);

        assertThatThrownBy(() -> servicio.registrar(comando(TipoEvento.ENTREGA, "Ana")))
                .isInstanceOf(EnvioYaEntregadoException.class);

        verifyNoInteractions(eventoRepository, outboxRepository);
    }

    @Test
    @DisplayName("Rechaza una transicion que no tiene sentido en la cadena logistica")
    void rechazaTransicionInvalida() {
        envioLocalEn(EstadoEnvio.REGISTRADO, null);

        assertThatThrownBy(() -> servicio.registrar(comando(TipoEvento.ENTREGA, "Ana")))
                .isInstanceOf(TransicionNoPermitidaException.class);

        verifyNoInteractions(eventoRepository, outboxRepository);
    }

    @Test
    @DisplayName("Rechaza un evento con fecha anterior al ultimo movimiento")
    void rechazaEventoFueraDeSecuencia() {
        envioLocalEn(EstadoEnvio.REGISTRADO, Instant.parse("2026-09-14T00:00:00Z"));

        assertThatThrownBy(() -> servicio.registrar(comando(TipoEvento.RECOGIDA, null)))
                .isInstanceOf(EventoFueraDeSecuenciaException.class);

        verifyNoInteractions(eventoRepository, outboxRepository);
    }

    private void envioLocalEn(EstadoEnvio estado, Instant ultimoEventoEn) {
        when(envioRastreadoRepository.buscar(new NumeroSeguimiento(NUMERO)))
                .thenReturn(Optional.of(new EnvioRastreado(
                        new NumeroSeguimiento(NUMERO), estado, ultimoEventoEn)));
    }

    private static RegistrarEventoComando comando(TipoEvento tipo, String recibidoPor) {
        return new RegistrarEventoComando(NUMERO, tipo, "CD-MDE", "Centro Medellin",
                "Medellin", OCURRIDO_EN, "Sin novedad", recibidoPor);
    }
}
