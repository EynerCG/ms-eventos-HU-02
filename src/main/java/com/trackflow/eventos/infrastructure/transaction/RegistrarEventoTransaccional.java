package com.trackflow.eventos.infrastructure.transaction;

import com.trackflow.eventos.application.port.in.RegistrarEventoComando;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.domain.model.Evento;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decorador que aporta la frontera transaccional sin obligar a la capa de aplicacion
 * a depender de Spring. El evento, el nuevo estado local y la fila del outbox se
 * confirman juntos o no se confirma ninguno.
 */
public class RegistrarEventoTransaccional implements RegistrarEventoUseCase {

    private final RegistrarEventoUseCase delegado;

    public RegistrarEventoTransaccional(RegistrarEventoUseCase delegado) {
        this.delegado = delegado;
    }

    @Override
    @Transactional
    public Evento registrar(RegistrarEventoComando comando) {
        return delegado.registrar(comando);
    }
}
