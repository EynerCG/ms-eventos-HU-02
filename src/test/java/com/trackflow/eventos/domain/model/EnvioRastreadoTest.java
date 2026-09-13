package com.trackflow.eventos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trackflow.eventos.domain.exception.EnvioYaEntregadoException;
import com.trackflow.eventos.domain.exception.EventoFueraDeSecuenciaException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvioRastreadoTest {

    private static final NumeroSeguimiento NUMERO = new NumeroSeguimiento("TRK-0001");
    private static final Instant AYER = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant HOY = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    @DisplayName("Aplicar un evento mueve el estado y la fecha del ultimo movimiento")
    void aplicaEstadoYFecha() {
        EnvioRastreado envio = new EnvioRastreado(NUMERO, EstadoEnvio.REGISTRADO, null);

        envio.aplicar(EstadoEnvio.EN_TRANSITO, HOY);

        assertThat(envio.estadoActual()).isEqualTo(EstadoEnvio.EN_TRANSITO);
        assertThat(envio.ultimoEventoEn()).isEqualTo(HOY);
        assertThat(envio.numeroSeguimiento()).isEqualTo(NUMERO);
    }

    @Test
    @DisplayName("Rechaza un evento con fecha anterior al ultimo registrado")
    void rechazaEventoFueraDeSecuencia() {
        EnvioRastreado envio = new EnvioRastreado(NUMERO, EstadoEnvio.EN_TRANSITO, HOY);

        assertThatThrownBy(() -> envio.aplicar(EstadoEnvio.EN_CENTRO_DISTRIBUCION, AYER))
                .isInstanceOf(EventoFueraDeSecuenciaException.class);
    }

    @Test
    @DisplayName("Un envio ya entregado no admite nuevos eventos")
    void rechazaEventoSobreEnvioEntregado() {
        EnvioRastreado envio = new EnvioRastreado(NUMERO, EstadoEnvio.ENTREGADO, AYER);

        assertThatThrownBy(envio::verificarAdmiteEventos)
                .isInstanceOf(EnvioYaEntregadoException.class)
                .hasMessageContaining("TRK-0001");

        assertThatThrownBy(() -> envio.aplicar(EstadoEnvio.EN_REPARTO, HOY))
                .isInstanceOf(EnvioYaEntregadoException.class);
    }

    @Test
    @DisplayName("Un envio que no esta entregado si admite eventos")
    void admiteEventosCuandoNoEstaEntregado() {
        EnvioRastreado envio = new EnvioRastreado(NUMERO, EstadoEnvio.EN_REPARTO, AYER);

        envio.verificarAdmiteEventos();

        assertThat(envio.estadoActual()).isEqualTo(EstadoEnvio.EN_REPARTO);
    }
}
