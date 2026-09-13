package com.trackflow.eventos.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByEstadoEnvioAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
            String estadoEnvio, Instant hasta, Limit limite);
}
