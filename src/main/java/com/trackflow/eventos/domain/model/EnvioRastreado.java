package com.trackflow.eventos.domain.model;

import com.trackflow.eventos.domain.exception.EnvioYaEntregadoException;
import com.trackflow.eventos.domain.exception.EventoFueraDeSecuenciaException;
import java.time.Instant;

/**
 * Proyeccion local del envio. Le permite a ms-eventos validar y registrar eventos
 * sin depender de que ms-envios este disponible en ese momento.
 */
public class EnvioRastreado {

    private final NumeroSeguimiento numeroSeguimiento;
    private EstadoEnvio estadoActual;
    private Instant ultimoEventoEn;

    public EnvioRastreado(NumeroSeguimiento numeroSeguimiento,
                          EstadoEnvio estadoActual,
                          Instant ultimoEventoEn) {
        this.numeroSeguimiento = numeroSeguimiento;
        this.estadoActual = estadoActual;
        this.ultimoEventoEn = ultimoEventoEn;
    }

    public void aplicar(EstadoEnvio nuevoEstado, Instant ocurridoEn) {
        if (estadoActual.esTerminal()) {
            throw new EnvioYaEntregadoException(numeroSeguimiento);
        }
        if (ultimoEventoEn != null && ocurridoEn.isBefore(ultimoEventoEn)) {
            throw new EventoFueraDeSecuenciaException(ocurridoEn, ultimoEventoEn);
        }
        this.estadoActual = nuevoEstado;
        this.ultimoEventoEn = ocurridoEn;
    }

    public void verificarAdmiteEventos() {
        if (estadoActual.esTerminal()) {
            throw new EnvioYaEntregadoException(numeroSeguimiento);
        }
    }

    public NumeroSeguimiento numeroSeguimiento() {
        return numeroSeguimiento;
    }

    public EstadoEnvio estadoActual() {
        return estadoActual;
    }

    public Instant ultimoEventoEn() {
        return ultimoEventoEn;
    }
}
