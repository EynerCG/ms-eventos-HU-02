package com.trackflow.eventos.infrastructure.persistence;

import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class EventoRepositoryAdapter implements EventoRepository {

    private final SpringDataEventoRepository repositorio;

    public EventoRepositoryAdapter(SpringDataEventoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void guardar(Evento evento) {
        repositorio.save(new EventoJpaEntity(
                evento.id(),
                evento.numeroSeguimiento().valor(),
                evento.tipo().name(),
                evento.estadoResultante().name(),
                evento.punto().codigo(),
                evento.punto().nombre(),
                evento.punto().ciudad(),
                evento.ocurridoEn(),
                evento.registradoEn(),
                evento.observaciones(),
                evento.recibidoPor()));
    }

    @Override
    public List<Evento> buscarPorNumeroSeguimiento(NumeroSeguimiento numeroSeguimiento) {
        return repositorio
                .findByNumeroSeguimientoOrderByOcurridoEnAsc(numeroSeguimiento.valor())
                .stream()
                .map(EventoRepositoryAdapter::aDominio)
                .toList();
    }

    private static Evento aDominio(EventoJpaEntity entidad) {
        return new Evento(
                entidad.getId(),
                new NumeroSeguimiento(entidad.getNumeroSeguimiento()),
                TipoEvento.valueOf(entidad.getTipoEvento()),
                EstadoEnvio.valueOf(entidad.getEstadoResultante()),
                new PuntoLogistico(entidad.getPuntoCodigo(), entidad.getPuntoNombre(),
                        entidad.getPuntoCiudad()),
                entidad.getOcurridoEn(),
                entidad.getRegistradoEn(),
                entidad.getObservaciones(),
                entidad.getRecibidoPor());
    }
}
