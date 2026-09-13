package com.trackflow.eventos.infrastructure.config;

import com.trackflow.eventos.application.port.in.ConsultarHistorialUseCase;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.application.port.out.EnvioRastreadoRepository;
import com.trackflow.eventos.application.port.out.EventoRepository;
import com.trackflow.eventos.application.port.out.OutboxRepository;
import com.trackflow.eventos.application.usecase.ConsultarHistorialService;
import com.trackflow.eventos.application.usecase.RegistrarEventoService;
import com.trackflow.eventos.domain.service.MaquinaEstadosEnvio;
import com.trackflow.eventos.infrastructure.transaction.RegistrarEventoTransaccional;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las capas de dominio y aplicacion no conocen Spring: se instancian aqui.
 */
@Configuration
public class BeanConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public MaquinaEstadosEnvio maquinaEstadosEnvio() {
        return new MaquinaEstadosEnvio();
    }

    @Bean
    public RegistrarEventoUseCase registrarEventoUseCase(EventoRepository eventoRepository,
                                                         EnvioRastreadoRepository envioRastreadoRepository,
                                                         EnvioEstadoPort envioEstadoPort,
                                                         OutboxRepository outboxRepository,
                                                         MaquinaEstadosEnvio maquinaEstados,
                                                         Clock reloj) {
        return new RegistrarEventoTransaccional(new RegistrarEventoService(
                eventoRepository, envioRastreadoRepository, envioEstadoPort,
                outboxRepository, maquinaEstados, reloj));
    }

    @Bean
    public ConsultarHistorialUseCase consultarHistorialUseCase(EventoRepository eventoRepository,
                                                               EnvioRastreadoRepository envioRastreadoRepository) {
        return new ConsultarHistorialService(eventoRepository, envioRastreadoRepository);
    }
}
