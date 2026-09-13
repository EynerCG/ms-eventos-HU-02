package com.trackflow.eventos.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento", schema = "eventos")
public class EventoJpaEntity {

    @Id
    private UUID id;

    @Column(name = "numero_seguimiento", nullable = false)
    private String numeroSeguimiento;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "estado_resultante", nullable = false)
    private String estadoResultante;

    @Column(name = "punto_codigo", nullable = false)
    private String puntoCodigo;

    @Column(name = "punto_nombre", nullable = false)
    private String puntoNombre;

    @Column(name = "punto_ciudad")
    private String puntoCiudad;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "recibido_por")
    private String recibidoPor;

    protected EventoJpaEntity() {
    }

    public EventoJpaEntity(UUID id, String numeroSeguimiento, String tipoEvento,
                           String estadoResultante, String puntoCodigo, String puntoNombre,
                           String puntoCiudad, Instant ocurridoEn, Instant registradoEn,
                           String observaciones, String recibidoPor) {
        this.id = id;
        this.numeroSeguimiento = numeroSeguimiento;
        this.tipoEvento = tipoEvento;
        this.estadoResultante = estadoResultante;
        this.puntoCodigo = puntoCodigo;
        this.puntoNombre = puntoNombre;
        this.puntoCiudad = puntoCiudad;
        this.ocurridoEn = ocurridoEn;
        this.registradoEn = registradoEn;
        this.observaciones = observaciones;
        this.recibidoPor = recibidoPor;
    }

    public UUID getId() {
        return id;
    }

    public String getNumeroSeguimiento() {
        return numeroSeguimiento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getEstadoResultante() {
        return estadoResultante;
    }

    public String getPuntoCodigo() {
        return puntoCodigo;
    }

    public String getPuntoNombre() {
        return puntoNombre;
    }

    public String getPuntoCiudad() {
        return puntoCiudad;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public String getRecibidoPor() {
        return recibidoPor;
    }
}
