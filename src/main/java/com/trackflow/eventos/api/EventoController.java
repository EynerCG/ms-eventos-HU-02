package com.trackflow.eventos.api;

import com.trackflow.eventos.api.dto.EventoResponse;
import com.trackflow.eventos.api.dto.HistorialResponse;
import com.trackflow.eventos.api.dto.RegistrarEventoRequest;
import com.trackflow.eventos.application.port.in.ConsultarHistorialUseCase;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.domain.model.Evento;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EventoController {

    private final RegistrarEventoUseCase registrarEvento;
    private final ConsultarHistorialUseCase consultarHistorial;

    public EventoController(RegistrarEventoUseCase registrarEvento,
                            ConsultarHistorialUseCase consultarHistorial) {
        this.registrarEvento = registrarEvento;
        this.consultarHistorial = consultarHistorial;
    }

    @PostMapping("/eventos")
    public ResponseEntity<EventoResponse> registrar(
            @Valid @RequestBody RegistrarEventoRequest request) {

        Evento evento = registrarEvento.registrar(request.aComando());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoResponse.desde(evento));
    }

    @GetMapping("/envios/{numeroSeguimiento}/eventos")
    public HistorialResponse historial(@PathVariable String numeroSeguimiento) {
        return HistorialResponse.desde(consultarHistorial.consultar(numeroSeguimiento));
    }
}
