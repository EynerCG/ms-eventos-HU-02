package com.trackflow.eventos.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackflow.eventos.application.port.in.ConsultarHistorialUseCase;
import com.trackflow.eventos.application.port.in.RegistrarEventoUseCase;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.exception.EnvioYaEntregadoException;
import com.trackflow.eventos.domain.exception.ServicioEnviosNoDisponibleException;
import com.trackflow.eventos.domain.model.EstadoEnvio;
import com.trackflow.eventos.domain.model.Evento;
import com.trackflow.eventos.domain.model.HistorialEnvio;
import com.trackflow.eventos.domain.model.NumeroSeguimiento;
import com.trackflow.eventos.domain.model.PuntoLogistico;
import com.trackflow.eventos.domain.model.TipoEvento;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventoController.class)
class EventoControllerTest {

    private static final NumeroSeguimiento NUMERO = new NumeroSeguimiento("TRK-0001");
    private static final Instant OCURRIDO_EN = Instant.parse("2026-09-13T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrarEventoUseCase registrarEvento;

    @MockBean
    private ConsultarHistorialUseCase consultarHistorial;

    @Test
    @DisplayName("AC1: registra el evento y devuelve 201 con el estado resultante")
    void registraEvento() throws Exception {
        when(registrarEvento.registrar(any()))
                .thenReturn(evento(TipoEvento.RECOGIDA, EstadoEnvio.EN_TRANSITO, null));

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RECOGIDA", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroSeguimiento").value("TRK-0001"))
                .andExpect(jsonPath("$.tipoEvento").value("RECOGIDA"))
                .andExpect(jsonPath("$.estadoResultante").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.punto.codigo").value("CD-MDE"));
    }

    @Test
    @DisplayName("AC2: la entrega devuelve el envio en estado ENTREGADO")
    void registraEntrega() throws Exception {
        when(registrarEvento.registrar(any()))
                .thenReturn(evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO, "Ana Perez"));

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ENTREGA", "Ana Perez")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoResultante").value("ENTREGADO"))
                .andExpect(jsonPath("$.recibidoPor").value("Ana Perez"));
    }

    @Test
    @DisplayName("AC3: un numero de seguimiento inexistente devuelve 404")
    void envioInexistente() throws Exception {
        when(registrarEvento.registrar(any()))
                .thenThrow(new EnvioNoEncontradoException(new NumeroSeguimiento("TRK-9999")));

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RECOGIDA", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Envio no encontrado"))
                .andExpect(jsonPath("$.detail").value(
                        "No existe un envio con el numero de seguimiento TRK-9999"));
    }

    @Test
    @DisplayName("Un evento sobre un envio ya entregado devuelve 409")
    void envioYaEntregado() throws Exception {
        when(registrarEvento.registrar(any())).thenThrow(new EnvioYaEntregadoException(NUMERO));

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ENTREGA", "Ana")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Evento no aplicable al envio"));
    }

    @Test
    @DisplayName("Un payload sin datos obligatorios devuelve 400 y no llega al caso de uso")
    void payloadIncompleto() throws Exception {
        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "numeroSeguimiento": "", "tipoEvento": null }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.numeroSeguimiento").exists())
                .andExpect(jsonPath("$.errores.tipoEvento").exists());

        verify(registrarEvento, never()).registrar(any());
    }

    @Test
    @DisplayName("Si ms-envios no responde al hidratar el envio, devuelve 503")
    void msEnviosNoDisponible() throws Exception {
        when(registrarEvento.registrar(any()))
                .thenThrow(new ServicioEnviosNoDisponibleException("connection refused"));

        mockMvc.perform(post("/api/v1/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("RECOGIDA", null)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("El historial devuelve el estado actual y los eventos en orden")
    void consultaHistorial() throws Exception {
        when(consultarHistorial.consultar("TRK-0001")).thenReturn(new HistorialEnvio(
                NUMERO, EstadoEnvio.ENTREGADO,
                List.of(evento(TipoEvento.ENTREGA, EstadoEnvio.ENTREGADO, "Ana"))));

        mockMvc.perform(get("/api/v1/envios/{numero}/eventos", "TRK-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("ENTREGADO"))
                .andExpect(jsonPath("$.eventos[0].tipoEvento").value("ENTREGA"));
    }

    private static String cuerpo(String tipoEvento, String recibidoPor) {
        String recibido = recibidoPor == null ? "null" : "\"" + recibidoPor + "\"";
        return """
                {
                  "numeroSeguimiento": "TRK-0001",
                  "tipoEvento": "%s",
                  "punto": { "codigo": "CD-MDE", "nombre": "Centro Medellin", "ciudad": "Medellin" },
                  "ocurridoEn": "2026-09-13T10:00:00Z",
                  "observaciones": "Sin novedad",
                  "recibidoPor": %s
                }
                """.formatted(tipoEvento, recibido);
    }

    private static Evento evento(TipoEvento tipo, EstadoEnvio estado, String recibidoPor) {
        return Evento.registrar(NUMERO, tipo, estado,
                new PuntoLogistico("CD-MDE", "Centro Medellin", "Medellin"),
                OCURRIDO_EN, Instant.parse("2026-09-13T11:00:00Z"), "Sin novedad", recibidoPor);
    }
}
