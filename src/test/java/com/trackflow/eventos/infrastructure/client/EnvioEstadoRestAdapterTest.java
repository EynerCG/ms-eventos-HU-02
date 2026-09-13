package com.trackflow.eventos.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.trackflow.eventos.domain.exception.ServicioEnviosNoDisponibleException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EnvioEstadoRestAdapterTest {

    private static final String BASE_URL = "http://ms-envios.local";
    private static final NumeroSeguimiento NUMERO = new NumeroSeguimiento("TRK-0001");

    private MockRestServiceServer servidor;
    private EnvioEstadoRestAdapter adaptador;

    @BeforeEach
    void prepararCliente() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adaptador = new EnvioEstadoRestAdapter(builder.build());
    }

    @Test
    @DisplayName("Traduce la respuesta de ms-envios al estado del dominio")
    void consultaEstadoExistente() {
        servidor.expect(requestTo(BASE_URL + "/api/v1/envios/TRK-0001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        { "numeroSeguimiento": "TRK-0001", "estado": "EN_REPARTO" }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adaptador.consultarEstado(NUMERO)).contains(EstadoEnvio.EN_REPARTO);
        servidor.verify();
    }

    @Test
    @DisplayName("Un 404 de ms-envios significa que el envio no existe, no un fallo")
    void consultaEstadoInexistente() {
        servidor.expect(requestTo(BASE_URL + "/api/v1/envios/TRK-0001"))
                .andRespond(withResourceNotFound());

        assertThat(adaptador.consultarEstado(NUMERO)).isEmpty();
        servidor.verify();
    }

    @Test
    @DisplayName("Un fallo de ms-envios se reporta como servicio no disponible")
    void consultaEstadoConFallo() {
        servidor.expect(requestTo(BASE_URL + "/api/v1/envios/TRK-0001"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adaptador.consultarEstado(NUMERO))
                .isInstanceOf(ServicioEnviosNoDisponibleException.class);
    }

    @Test
    @DisplayName("Envia el cambio de estado con el idEvento como clave de idempotencia")
    void actualizaEstado() {
        UUID idEvento = UUID.randomUUID();
        servidor.expect(requestTo(BASE_URL + "/api/v1/envios/TRK-0001/estado"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.idEvento").value(idEvento.toString()))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        adaptador.actualizarEstado(NUMERO, EstadoEnvio.ENTREGADO,
                Instant.parse("2026-09-13T10:00:00Z"), idEvento);

        servidor.verify();
    }

    @Test
    @DisplayName("Si ms-envios rechaza la actualizacion, se reporta como no disponible")
    void actualizaEstadoConFallo() {
        servidor.expect(requestTo(BASE_URL + "/api/v1/envios/TRK-0001/estado"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adaptador.actualizarEstado(NUMERO, EstadoEnvio.ENTREGADO,
                Instant.parse("2026-09-13T10:00:00Z"), UUID.randomUUID()))
                .isInstanceOf(ServicioEnviosNoDisponibleException.class);
    }
}
