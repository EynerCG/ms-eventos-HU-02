package com.trackflow.eventos.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trackflow.eventos.application.port.in.RegistrarEventoComando;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarEventoTransaccionalTest {

    @Mock
    private RegistrarEventoUseCase delegado;

    @Test
    @DisplayName("Delega el registro sin alterar el resultado del caso de uso")
    void delegaElRegistro() {
        RegistrarEventoComando comando = new RegistrarEventoComando(
                "TRK-0001", TipoEvento.RECOGIDA, "CD-MDE", "Centro Medellin", "Medellin",
                Instant.parse("2026-09-13T10:00:00Z"), null, null);
        Evento esperado = Evento.registrar(
                new NumeroSeguimiento("TRK-0001"), TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO,
                new PuntoLogistico("CD-MDE", "Centro Medellin", "Medellin"),
                Instant.parse("2026-09-13T10:00:00Z"), Instant.parse("2026-09-13T11:00:00Z"),
                null, null);
        when(delegado.registrar(comando)).thenReturn(esperado);

        Evento resultado = new RegistrarEventoTransaccional(delegado).registrar(comando);

        assertThat(resultado).isEqualTo(esperado);
        verify(delegado).registrar(comando);
    }
}
