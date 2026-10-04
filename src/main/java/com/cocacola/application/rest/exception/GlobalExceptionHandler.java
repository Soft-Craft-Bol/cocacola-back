package com.cocacola.application.rest.exception;

import com.cocacola.application.response.ErrorResponse;
import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.helpers.TooManyRequestsException;
import com.cocacola.domain.helpers.UnauthorizedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Todas las respuestas de error tienen la forma { "message": "..." }. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(org.springframework.security.access.AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("name", "Nombre"), Map.entry("firstName", "Nombre"), Map.entry("lastName", "Apellido"),
            Map.entry("phone", "Celular"), Map.entry("email", "Correo"), Map.entry("password", "Contraseña"),
            Map.entry("role", "Rol"), Map.entry("type", "Tipo"), Map.entry("date", "Fecha"),
            Map.entry("location", "Lugar"), Map.entry("eventId", "Evento"), Map.entry("participantId", "Participante"),
            Map.entry("rating", "Calificación"), Map.entry("nps", "NPS"), Map.entry("organization", "Organización"),
            Map.entry("service", "Atención"), Map.entry("experiences", "Experiencias"), Map.entry("products", "Productos"),
            Map.entry("overall", "Experiencia general"));

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noRoute() {
        return build(HttpStatus.NOT_FOUND, "Ruta no encontrada");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> wrongMethod() {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ErrorResponse> tooMany(TooManyRequestsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ErrorResponse> storage(StorageException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge() {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el máximo de 5 MB");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> FIELD_LABELS.getOrDefault(e.getField(), e.getField()) + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("Datos inválidos");
        return build(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex instanceof IllegalArgumentException ? ex.getMessage() : "Cuerpo de la solicitud inválido");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
