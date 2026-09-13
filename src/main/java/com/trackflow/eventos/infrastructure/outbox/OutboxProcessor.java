package com.trackflow.eventos.infrastructure.outbox;

import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica hacia ms-envios los cambios de estado que quedaron pendientes.
 * Es lo que permite que ms-eventos siga aceptando eventos cuando ms-envios esta caido.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final EnvioEstadoPort envioEstadoPort;
    private final Clock reloj;
    private final int maximoIntentos;
    private final Duration esperaBase;
    private final int tamanoLote;

    public OutboxProcessor(OutboxRepository outboxRepository,
                           EnvioEstadoPort envioEstadoPort,
                           Clock reloj,
                           @Value("${trackflow.outbox.maximo-intentos:8}") int maximoIntentos,
                           @Value("${trackflow.outbox.espera-base-segundos:5}") long esperaBaseSegundos,
                           @Value("${trackflow.outbox.tamano-lote:50}") int tamanoLote) {
        this.outboxRepository = outboxRepository;
        this.envioEstadoPort = envioEstadoPort;
        this.reloj = reloj;
        this.maximoIntentos = maximoIntentos;
        this.esperaBase = Duration.ofSeconds(esperaBaseSegundos);
        this.tamanoLote = tamanoLote;
    }

    @Scheduled(fixedDelayString = "${trackflow.outbox.intervalo-ms:5000}")
    @Transactional
    public void sincronizarPendientes() {
        Instant ahora = reloj.instant();
        List<MensajeOutbox> pendientes = outboxRepository.pendientes(ahora, tamanoLote);

        for (MensajeOutbox mensaje : pendientes) {
            sincronizar(mensaje, ahora);
        }
    }

    private void sincronizar(MensajeOutbox mensaje, Instant ahora) {
        try {
            envioEstadoPort.actualizarEstado(
                    new NumeroSeguimiento(mensaje.numeroSeguimiento()),
                    EstadoEnvio.valueOf(mensaje.estado()),
                    mensaje.ocurridoEn(),
                    mensaje.idEvento());
            outboxRepository.marcarEnviado(mensaje.idEvento(), ahora);
        } catch (RuntimeException e) {
            manejarFallo(mensaje, ahora, e);
        }
    }

    private void manejarFallo(MensajeOutbox mensaje, Instant ahora, RuntimeException causa) {
        int intentosRealizados = mensaje.intentos() + 1;
        if (intentosRealizados >= maximoIntentos) {
            log.error("Se agotaron los reintentos para sincronizar el evento {} con ms-envios",
                    mensaje.idEvento(), causa);
            outboxRepository.marcarFallido(mensaje.idEvento(), causa.getMessage());
            return;
        }

        Instant proximoIntento = ahora.plus(esperaBase.multipliedBy(1L << mensaje.intentos()));
        log.warn("Fallo la sincronizacion del evento {} con ms-envios, se reintenta en {}",
                mensaje.idEvento(), proximoIntento);
        outboxRepository.reprogramar(mensaje.idEvento(), proximoIntento, causa.getMessage());
    }
}
