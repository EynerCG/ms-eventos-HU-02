package com.trackflow.eventos.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import com.trackflow.eventos.domain.exception.ServicioEnviosNoDisponibleException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    private static final Instant AHORA = Instant.parse("2026-09-13T12:00:00Z");
    private static final UUID ID_EVENTO = UUID.randomUUID();

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private EnvioEstadoPort envioEstadoPort;

    @Test
    @DisplayName("Sincroniza con ms-envios y marca el mensaje como enviado")
    void sincronizaPendiente() {
        when(outboxRepository.pendientes(AHORA, 50)).thenReturn(List.of(mensaje(0)));

        procesador(8).sincronizarPendientes();

        verify(envioEstadoPort).actualizarEstado(
                new NumeroSeguimiento("TRK-0001"), EstadoEnvio.ENTREGADO,
                Instant.parse("2026-09-13T10:00:00Z"), ID_EVENTO);
        verify(outboxRepository).marcarEnviado(ID_EVENTO, AHORA);
    }

    @Test
    @DisplayName("Si ms-envios falla, reprograma el reintento con espera exponencial")
    void reprogramaConBackoff() {
        when(outboxRepository.pendientes(any(), eq(50))).thenReturn(List.of(mensaje(2)));
        doThrow(new ServicioEnviosNoDisponibleException("timeout"))
                .when(envioEstadoPort).actualizarEstado(any(), any(), any(), any());

        procesador(8).sincronizarPendientes();

        ArgumentCaptor<Instant> proximoIntento = ArgumentCaptor.forClass(Instant.class);
        verify(outboxRepository).reprogramar(eq(ID_EVENTO), proximoIntento.capture(), any());
        assertThat(proximoIntento.getValue()).isEqualTo(AHORA.plusSeconds(20));
        verify(outboxRepository, never()).marcarEnviado(any(), any());
    }

    @Test
    @DisplayName("Al agotar los reintentos marca el mensaje como fallido")
    void marcaFallidoAlAgotarReintentos() {
        when(outboxRepository.pendientes(any(), eq(50))).thenReturn(List.of(mensaje(2)));
        doThrow(new ServicioEnviosNoDisponibleException("timeout"))
                .when(envioEstadoPort).actualizarEstado(any(), any(), any(), any());

        procesador(3).sincronizarPendientes();

        verify(outboxRepository).marcarFallido(eq(ID_EVENTO), any());
        verify(outboxRepository, never()).reprogramar(any(), any(), any());
    }

    private OutboxProcessor procesador(int maximoIntentos) {
        return new OutboxProcessor(outboxRepository, envioEstadoPort,
                Clock.fixed(AHORA, ZoneOffset.UTC), maximoIntentos, 5, 50);
    }

    private static MensajeOutbox mensaje(int intentos) {
        return new MensajeOutbox(ID_EVENTO, "TRK-0001", "ENTREGADO",
                Instant.parse("2026-09-13T10:00:00Z"), intentos);
    }
}
