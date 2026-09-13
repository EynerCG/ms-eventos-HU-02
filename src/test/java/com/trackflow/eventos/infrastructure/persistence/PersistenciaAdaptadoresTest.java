package com.trackflow.eventos.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EventoRepositoryAdapter.class, EnvioRastreadoRepositoryAdapter.class,
        OutboxRepositoryAdapter.class})
class PersistenciaAdaptadoresTest {

    private static final NumeroSeguimiento NUMERO = new NumeroSeguimiento("TRK-0001");
    private static final Instant AHORA = Instant.parse("2026-09-13T12:00:00Z");

    @Autowired
    private EventoRepositoryAdapter eventos;
    @Autowired
    private EnvioRastreadoRepositoryAdapter enviosRastreados;
    @Autowired
    private OutboxRepositoryAdapter outbox;

    @Test
    @DisplayName("Guarda eventos y los devuelve en orden cronologico")
    void guardaYRecuperaEventos() {
        Evento entrega = evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO,
                Instant.parse("2026-09-13T10:00:00Z"), "Ana Perez");
        Evento recogida = evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO,
                Instant.parse("2026-09-11T10:00:00Z"), null);

        eventos.guardar(entrega);
        eventos.guardar(recogida);

        List<Evento> historial = eventos.buscarPorNumeroSeguimiento(NUMERO);

        assertThat(historial).containsExactly(recogida, entrega);
        assertThat(historial.get(1).recibidoPor()).isEqualTo("Ana Perez");
        assertThat(historial.get(1).punto().nombre()).isEqualTo("Centro Medellin");
    }

    @Test
    @DisplayName("Guarda y recupera la proyeccion local del envio")
    void guardaYRecuperaEnvioRastreado() {
        enviosRastreados.guardar(new EnvioRastreado(NUMERO, EstadoEnvio.EN_REPARTO, AHORA));

        EnvioRastreado recuperado = enviosRastreados.buscar(NUMERO).orElseThrow();

        assertThat(recuperado.estadoActual()).isEqualTo(EstadoEnvio.EN_REPARTO);
        assertThat(recuperado.ultimoEventoEn()).isEqualTo(AHORA);
        assertThat(enviosRastreados.buscar(new NumeroSeguimiento("TRK-9999"))).isEmpty();
    }

    @Test
    @DisplayName("Encola, reprograma y finalmente marca como enviado un mensaje del outbox")
    void cicloDeVidaDelOutbox() {
        Evento evento = evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO, AHORA, "Ana");
        outbox.encolar(MensajeOutbox.desde(evento));

        assertThat(outbox.pendientes(AHORA, 10)).singleElement()
                .satisfies(mensaje -> {
                    assertThat(mensaje.idEvento()).isEqualTo(evento.id());
                    assertThat(mensaje.estado()).isEqualTo("ENTREGADO");
                    assertThat(mensaje.intentos()).isZero();
                });

        outbox.reprogramar(evento.id(), AHORA.plusSeconds(60), "timeout");
        assertThat(outbox.pendientes(AHORA, 10)).isEmpty();
        assertThat(outbox.pendientes(AHORA.plusSeconds(120), 10)).singleElement()
                .satisfies(mensaje -> assertThat(mensaje.intentos()).isEqualTo(1));

        outbox.marcarEnviado(evento.id(), AHORA.plusSeconds(120));
        assertThat(outbox.pendientes(AHORA.plusSeconds(200), 10)).isEmpty();
    }

    @Test
    @DisplayName("Un mensaje marcado como fallido sale de la cola de pendientes")
    void mensajeFallidoSaleDeLaCola() {
        Evento evento = evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO, AHORA, null);
        outbox.encolar(MensajeOutbox.desde(evento));

        outbox.marcarFallido(evento.id(), "ms-envios no responde");

        assertThat(outbox.pendientes(AHORA.plusSeconds(600), 10)).isEmpty();
    }

    private static Evento evento(TipoEvento tipo, EstadoEnvio estado, Instant ocurridoEn,
                                 String recibidoPor) {
        return Evento.registrar(NUMERO, tipo, estado,
                new PuntoLogistico("CD-MDE", "Centro Medellin", "Medellin"),
                ocurridoEn, AHORA, "Sin novedad", recibidoPor);
    }
}
