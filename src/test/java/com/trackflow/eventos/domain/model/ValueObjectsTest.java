package com.trackflow.eventos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trackflow.eventos.domain.exception.DatoInvalidoException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @Test
    @DisplayName("El numero de seguimiento se normaliza a mayusculas")
    void normalizaNumeroSeguimiento() {
        assertThat(new NumeroSeguimiento(" trk-0001 ").valor()).isEqualTo("TRK-0001");
        assertThat(new NumeroSeguimiento("TRK-0001")).hasToString("TRK-0001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "TRK 0001", "abc", "TRK_0001"})
    @DisplayName("Rechaza numeros de seguimiento vacios o con formato invalido")
    void rechazaNumeroSeguimientoInvalido(String valor) {
        assertThatThrownBy(() -> new NumeroSeguimiento(valor))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test
    @DisplayName("El punto logistico normaliza codigo y nombre")
    void normalizaPunto() {
        PuntoLogistico punto = new PuntoLogistico(" cd-mde ", " Centro Medellin ", " Medellin ");

        assertThat(punto.codigo()).isEqualTo("CD-MDE");
        assertThat(punto.nombre()).isEqualTo("Centro Medellin");
        assertThat(punto.ciudad()).isEqualTo("Medellin");
    }

    @Test
    @DisplayName("El punto logistico exige codigo y nombre")
    void rechazaPuntoIncompleto() {
        assertThatThrownBy(() -> new PuntoLogistico(null, "Centro", "Medellin"))
                .isInstanceOf(DatoInvalidoException.class);
        assertThatThrownBy(() -> new PuntoLogistico("CD-MDE", " ", "Medellin"))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test
    @DisplayName("Solo el evento de entrega conserva quien recibio")
    void recibidoPorSoloEnEntrega() {
        Evento entrega = evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO, "Ana Perez");
        Evento transito = evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO, "Ana Perez");

        assertThat(entrega.recibidoPor()).isEqualTo("Ana Perez");
        assertThat(transito.recibidoPor()).isNull();
    }

    @Test
    @DisplayName("El evento exige sus datos obligatorios")
    void rechazaEventoIncompleto() {
        assertThatThrownBy(() -> evento(null, EstadoEnvio.EN_TRANSITO, null))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test
    @DisplayName("El historial se ordena cronologicamente y expone el ultimo movimiento")
    void historialOrdenadoCronologicamente() {
        Evento reciente = evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO, "Ana",
                Instant.parse("2026-09-13T10:00:00Z"));
        Evento antiguo = evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO, null,
                Instant.parse("2026-09-11T10:00:00Z"));

        HistorialEnvio historial = new HistorialEnvio(
                new NumeroSeguimiento("TRK-0001"), EstadoEnvio.ENTREGADO,
                List.of(reciente, antiguo));

        assertThat(historial.eventos()).containsExactly(antiguo, reciente);
        assertThat(historial.ultimoMovimiento()).contains(reciente);
    }

    @Test
    @DisplayName("Un historial sin eventos no tiene ultimo movimiento")
    void historialVacio() {
        HistorialEnvio historial = new HistorialEnvio(
                new NumeroSeguimiento("TRK-0001"), EstadoEnvio.REGISTRADO, List.of());

        assertThat(historial.ultimoMovimiento()).isEmpty();
    }

    private static Evento evento(TipoEvento tipo, EstadoEnvio estado, String recibidoPor) {
        return evento(tipo, estado, recibidoPor, Instant.parse("2026-09-13T10:00:00Z"));
    }

    private static Evento evento(TipoEvento tipo, EstadoEnvio estado, String recibidoPor,
                                 Instant ocurridoEn) {
        return Evento.registrar(
                new NumeroSeguimiento("TRK-0001"), tipo, estado,
                new PuntoLogistico("CD-MDE", "Centro Medellin", "Medellin"),
                ocurridoEn, Instant.parse("2026-09-13T11:00:00Z"), null, recibidoPor);
    }
}
