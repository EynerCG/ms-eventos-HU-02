package com.trackflow.eventos.infrastructure.persistence;

import com.trackflow.eventos.application.port.out.MensajeOutbox;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final SpringDataOutboxRepository repositorio;

    public OutboxRepositoryAdapter(SpringDataOutboxRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void encolar(MensajeOutbox mensaje) {
        repositorio.save(new OutboxJpaEntity(
                mensaje.idEvento(),
                mensaje.numeroSeguimiento(),
                mensaje.estado(),
                mensaje.ocurridoEn(),
                Instant.EPOCH));
    }

    @Override
    public List<MensajeOutbox> pendientes(Instant hasta, int limite) {
        return repositorio
                .findByEstadoEnvioAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
                        "PENDIENTE", hasta, Limit.of(limite))
                .stream()
                .map(entidad -> new MensajeOutbox(
                        entidad.getIdEvento(),
                        entidad.getNumeroSeguimiento(),
                        entidad.getEstado(),
                        entidad.getOcurridoEn(),
                        entidad.getIntentos()))
                .toList();
    }

    @Override
    public void marcarEnviado(UUID idEvento, Instant sincronizadoEn) {
        repositorio.findById(idEvento)
                .ifPresent(entidad -> entidad.marcarEnviado(sincronizadoEn));
    }

    @Override
    public void reprogramar(UUID idEvento, Instant proximoIntentoEn, String ultimoError) {
        repositorio.findById(idEvento)
                .ifPresent(entidad -> entidad.reprogramar(proximoIntentoEn, ultimoError));
    }

    @Override
    public void marcarFallido(UUID idEvento, String ultimoError) {
        repositorio.findById(idEvento)
                .ifPresent(entidad -> entidad.marcarFallido(ultimoError));
    }
}
