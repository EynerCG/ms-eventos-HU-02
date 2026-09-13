package com.trackflow.eventos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEnvioRastreadoRepository
        extends JpaRepository<EnvioRastreadoJpaEntity, String> {
}
