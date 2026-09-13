package com.trackflow.eventos.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "envio_rastreado", schema = "eventos")
public class EnvioRastreadoJpaEntity {

    @Id
    @Column(name = "numero_seguimiento")
    private String numeroSeguimiento;

    @Column(name = "estado_actual", nullable = false)
    private String estadoActual;

    @Column(name = "ultimo_evento_en")
    private Instant ultimoEventoEn;

    protected EnvioRastreadoJpaEntity() {
    }

    public EnvioRastreadoJpaEntity(String numeroSeguimiento, String estadoActual,
                                   Instant ultimoEventoEn) {
        this.numeroSeguimiento = numeroSeguimiento;
        this.estadoActual = estadoActual;
        this.ultimoEventoEn = ultimoEventoEn;
    }

    public String getNumeroSeguimiento() {
        return numeroSeguimiento;
    }

    public String getEstadoActual() {
        return estadoActual;
    }

    public Instant getUltimoEventoEn() {
        return ultimoEventoEn;
    }
}
