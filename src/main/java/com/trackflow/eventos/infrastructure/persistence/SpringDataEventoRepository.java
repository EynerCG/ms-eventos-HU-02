package com.trackflow.eventos.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEventoRepository extends JpaRepository<EventoJpaEntity, UUID> {

    List<EventoJpaEntity> findByNumeroSeguimientoOrderByOcurridoEnAsc(String numeroSeguimiento);
}
