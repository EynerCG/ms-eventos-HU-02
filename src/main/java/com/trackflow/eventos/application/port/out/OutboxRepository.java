package com.trackflow.eventos.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void encolar(MensajeOutbox mensaje);

    List<MensajeOutbox> pendientes(Instant hasta, int limite);

    void marcarEnviado(UUID idEvento, Instant sincronizadoEn);

    void reprogramar(UUID idEvento, Instant proximoIntentoEn, String ultimoError);

    void marcarFallido(UUID idEvento, String ultimoError);
}
