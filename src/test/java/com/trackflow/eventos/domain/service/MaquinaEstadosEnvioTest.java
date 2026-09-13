package com.trackflow.eventos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trackflow.eventos.domain.exception.TransicionNoPermitidaException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.TipoEvento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaquinaEstadosEnvioTest {

    private final MaquinaEstadosEnvio maquina = new MaquinaEstadosEnvio();

    @ParameterizedTest(name = "{0} sobre un envio {1} lo deja en {2}")
    @CsvSource({
            "RECOGIDA,                REGISTRADO,             EN_TRANSITO",
            "LLEGADA_CENTRO,          EN_TRANSITO,            EN_CENTRO_DISTRIBUCION",
            "SALIDA_CENTRO,           EN_CENTRO_DISTRIBUCION, EN_TRANSITO",
            "SALIDA_REPARTO,          EN_TRANSITO,            EN_REPARTO",
            "SALIDA_REPARTO,          EN_CENTRO_DISTRIBUCION, EN_REPARTO",
            "SALIDA_REPARTO,          ENTREGA_FALLIDA,        EN_REPARTO",
            "INTENTO_ENTREGA_FALLIDO, EN_REPARTO,             ENTREGA_FALLIDA",
            "ENTREGA,                 EN_REPARTO,             ENTREGADO"
    })
    @DisplayName("Aplica el estado que corresponde a cada evento valido")
    void transicionesValidas(TipoEvento tipo, EstadoEnvio origen, EstadoEnvio esperado) {
        assertThat(maquina.siguienteEstado(origen, tipo)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "{0} sobre un envio {1} se rechaza")
    @CsvSource({
            "RECOGIDA,                EN_TRANSITO",
            "RECOGIDA,                EN_REPARTO",
            "LLEGADA_CENTRO,          REGISTRADO",
            "SALIDA_CENTRO,           EN_TRANSITO",
            "SALIDA_REPARTO,          REGISTRADO",
            "INTENTO_ENTREGA_FALLIDO, EN_TRANSITO",
            "ENTREGA,                 REGISTRADO",
            "ENTREGA,                 EN_TRANSITO",
            "ENTREGA,                 ENTREGADO"
    })
    @DisplayName("Rechaza las transiciones que no tienen sentido en la cadena logistica")
    void transicionesInvalidas(TipoEvento tipo, EstadoEnvio origen) {
        assertThatThrownBy(() -> maquina.siguienteEstado(origen, tipo))
                .isInstanceOf(TransicionNoPermitidaException.class)
                .hasMessageContaining(tipo.name())
                .hasMessageContaining(origen.name());
    }

    @Test
    @DisplayName("ENTREGADO es el unico estado terminal")
    void entregadoEsTerminal() {
        assertThat(EstadoEnvio.ENTREGADO.esTerminal()).isTrue();
        assertThat(EstadoEnvio.EN_REPARTO.esTerminal()).isFalse();
        assertThat(EstadoEnvio.REGISTRADO.esTerminal()).isFalse();
    }
}
