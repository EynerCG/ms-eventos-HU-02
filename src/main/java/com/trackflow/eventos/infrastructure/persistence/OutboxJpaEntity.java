package com.trackflow.eventos.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_estado", schema = "eventos")
public class OutboxJpaEntity {

    @Id
    @Column(name = "id_evento")
    private UUID idEvento;

    @Column(name = "numero_seguimiento", nullable = false)
    private String numeroSeguimiento;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "estado_envio", nullable = false)
    private String estadoEnvio;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "proximo_intento_en", nullable = false)
    private Instant proximoIntentoEn;

    @Column(name = "sincronizado_en")
    private Instant sincronizadoEn;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;

    protected OutboxJpaEntity() {
    }

    public OutboxJpaEntity(UUID idEvento, String numeroSeguimiento, String estado,
                           Instant ocurridoEn, Instant proximoIntentoEn) {
        this.idEvento = idEvento;
        this.numeroSeguimiento = numeroSeguimiento;
        this.estado = estado;
        this.ocurridoEn = ocurridoEn;
        this.estadoEnvio = "PENDIENTE";
        this.intentos = 0;
        this.proximoIntentoEn = proximoIntentoEn;
    }

    public void marcarEnviado(Instant sincronizadoEn) {
        this.estadoEnvio = "ENVIADO";
        this.sincronizadoEn = sincronizadoEn;
        this.ultimoError = null;
    }

    public void reprogramar(Instant proximoIntentoEn, String ultimoError) {
        this.intentos++;
        this.proximoIntentoEn = proximoIntentoEn;
        this.ultimoError = recortar(ultimoError);
    }

    public void marcarFallido(String ultimoError) {
        this.intentos++;
        this.estadoEnvio = "FALLIDO";
        this.ultimoError = recortar(ultimoError);
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() > 1000 ? texto.substring(0, 1000) : texto;
    }

    public UUID getIdEvento() {
        return idEvento;
    }

    public String getNumeroSeguimiento() {
        return numeroSeguimiento;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public String getEstadoEnvio() {
        return estadoEnvio;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getProximoIntentoEn() {
        return proximoIntentoEn;
    }

    public Instant getSincronizadoEn() {
        return sincronizadoEn;
    }

    public String getUltimoError() {
        return ultimoError;
    }
}
