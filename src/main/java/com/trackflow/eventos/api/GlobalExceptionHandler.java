package com.trackflow.eventos.api;

import com.trackflow.eventos.domain.exception.DatoInvalidoException;
import com.trackflow.eventos.domain.exception.EnvioNoEncontradoException;
import com.trackflow.eventos.domain.exception.EnvioYaEntregadoException;
import com.trackflow.eventos.domain.exception.EventoFueraDeSecuenciaException;
import com.trackflow.eventos.domain.exception.ServicioEnviosNoDisponibleException;
import com.trackflow.eventos.domain.exception.TransicionNoPermitidaException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EnvioNoEncontradoException.class)
    public ProblemDetail envioNoEncontrado(EnvioNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Envio no encontrado", e.getMessage());
    }

    @ExceptionHandler({TransicionNoPermitidaException.class,
            EnvioYaEntregadoException.class,
            EventoFueraDeSecuenciaException.class})
    public ProblemDetail conflicto(RuntimeException e) {
        return problema(HttpStatus.CONFLICT, "Evento no aplicable al envio", e.getMessage());
    }

    @ExceptionHandler(DatoInvalidoException.class)
    public ProblemDetail datoInvalido(DatoInvalidoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Datos invalidos", e.getMessage());
    }

    @ExceptionHandler(ServicioEnviosNoDisponibleException.class)
    public ProblemDetail servicioNoDisponible(ServicioEnviosNoDisponibleException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE,
                "ms-envios no disponible", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "invalido" : error.getDefaultMessage(),
                        (primero, segundo) -> primero));

        ProblemDetail detalle = problema(HttpStatus.BAD_REQUEST, "Datos invalidos",
                "Faltan datos obligatorios o tienen un formato incorrecto");
        detalle.setProperty("errores", errores);
        return detalle;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
