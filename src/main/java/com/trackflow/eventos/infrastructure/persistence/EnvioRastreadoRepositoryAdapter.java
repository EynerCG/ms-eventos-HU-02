package com.trackflow.eventos.infrastructure.persistence;

import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.domain.model.EnvioRastreado;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class EnvioRastreadoRepositoryAdapter implements EnvioRastreadoRepository {

    private final SpringDataEnvioRastreadoRepository repositorio;

    public EnvioRastreadoRepositoryAdapter(SpringDataEnvioRastreadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Optional<EnvioRastreado> buscar(NumeroSeguimiento numeroSeguimiento) {
        return repositorio.findById(numeroSeguimiento.valor())
                .map(entidad -> new EnvioRastreado(
                        new NumeroSeguimiento(entidad.getNumeroSeguimiento()),
                        EstadoEnvio.valueOf(entidad.getEstadoActual()),
                        entidad.getUltimoEventoEn()));
    }

    @Override
    public void guardar(EnvioRastreado envio) {
        repositorio.save(new EnvioRastreadoJpaEntity(
                envio.numeroSeguimiento().valor(),
                envio.estadoActual().name(),
                envio.ultimoEventoEn()));
    }
}
