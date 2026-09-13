package com.trackflow.eventos.infrastructure.client;

import com.trackflow.eventos.application.port.out.EnvioEstadoPort;
import com.trackflow.eventos.domain.exception.ServicioEnviosNoDisponibleException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class EnvioEstadoRestAdapter implements EnvioEstadoPort {

    private static final String RUTA_ENVIO = "/api/v1/envios/{numeroSeguimiento}";
    private static final String RUTA_ESTADO = "/api/v1/envios/{numeroSeguimiento}/estado";

    private final RestClient restClient;

    public EnvioEstadoRestAdapter(RestClient restClientMsEnvios) {
        this.restClient = restClientMsEnvios;
    }

    @Override
    public Optional<EstadoEnvio> consultarEstado(NumeroSeguimiento numeroSeguimiento) {
        try {
            RespuestaEnvio respuesta = restClient.get()
                    .uri(RUTA_ENVIO, numeroSeguimiento.valor())
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, (req, res) -> {
                        // Un 404 significa "no existe", no un fallo de comunicacion.
                    })
                    .body(RespuestaEnvio.class);

            return Optional.ofNullable(respuesta)
                    .map(RespuestaEnvio::estado)
                    .map(EstadoEnvio::valueOf);
        } catch (RestClientException e) {
            throw new ServicioEnviosNoDisponibleException(e.getMessage());
        }
    }

    @Override
    public void actualizarEstado(NumeroSeguimiento numeroSeguimiento, EstadoEnvio estado,
                                 Instant ocurridoEn, UUID idEvento) {
        try {
            restClient.patch()
                    .uri(RUTA_ESTADO, numeroSeguimiento.valor())
                    .body(Map.of(
                            "estado", estado.name(),
                            "ocurridoEn", ocurridoEn.toString(),
                            "idEvento", idEvento.toString()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ServicioEnviosNoDisponibleException(e.getMessage());
        }
    }

    private record RespuestaEnvio(String numeroSeguimiento, String estado) {
    }
}
