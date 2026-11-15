package com.example.app.shared.infrastructure.in.web;

import com.example.app.shared.application.exception.ResourceNotFoundException;
import com.example.app.shared.domain.ConflictException;
import com.example.app.shared.domain.ValidationException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.time.Clock;

/**
 * Last-resort error mapping for every controller, as {@link ErrorResponse}. Slice advices (ordered first) handle
 * their own exceptions; the inherited ResponseEntityExceptionHandler covers framework 4xx, converted in
 * {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ErrorResponse> invalid(ValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage()).withField(e.field()).toResponseEntity();
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, e.getMessage()).withField(e.field()).toResponseEntity();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage()).toResponseEntity();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        String state = sqlState(e);
        if ("23505".equals(state)) {
            String constraint = constraintName(e);
            return error(HttpStatus.CONFLICT, duplicateMessage(constraint)).withField(duplicateField(constraint))
                    .toResponseEntity();
        }
        if ("23503".equals(state)) {
            return error(HttpStatus.CONFLICT, "Hay un dato relacionado que no existe o todavía está en uso")
                    .toResponseEntity();
        }
        LOG.warn("Unmapped data integrity violation", e);
        return error(HttpStatus.CONFLICT, statusMessage(409)).toResponseEntity();
    }

    /** A failed @PreAuthorize is thrown inside the controller, so it lands here instead of the security filter. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "No hay permiso para esta acción").toResponseEntity();
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> stale(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "Los datos cambiaron mientras tanto; volvé a intentarlo").toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        LOG.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, statusMessage(500)).toResponseEntity();
    }

    /** Framework errors (bad param, unreadable body, 405, 415, ...): same status and headers, a fixed Spanish message (5xx: generic and logged); Spring's English detail never reaches the client. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> problem = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (problem == null) return null; // response already committed
        HttpStatusCode status = problem.getStatusCode();
        if (status.is5xxServerError()) LOG.error("Unhandled framework exception", ex);
        ErrorResponse error = error(status, frameworkMessage(ex, status));
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) error = error.withField(mismatch.getName());
        return ResponseEntity.status(status).headers(problem.getHeaders()).contentType(MediaType.APPLICATION_JSON)
                .body(error);
    }

    private ErrorResponse error(HttpStatusCode status, String message) {
        return ErrorResponse.of(status, message, clock);
    }

    private static String frameworkMessage(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) return statusMessage(status.value());
        if (ex instanceof HttpMessageNotReadableException) return "El cuerpo de la solicitud no es válido";
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "El parámetro " + mismatch.getName() + " no tiene un valor válido";
        }
        return statusMessage(status.value());
    }

    private static String duplicateField(String constraint) {
        return "app_user_email_key".equalsIgnoreCase(constraint) ? "email" : null;
    }

    private static String duplicateMessage(String constraint) {
        return "app_user_email_key".equalsIgnoreCase(constraint)
                ? "Ya existe un usuario con ese email" : "Ya existe un registro con ese valor";
    }

    private static String statusMessage(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es válida";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido";
            case 406 -> "Formato de respuesta no soportado";
            case 409 -> "La solicitud entra en conflicto con datos existentes";
            case 415 -> "Formato de contenido no soportado";
            case 500 -> "Ocurrió un error inesperado";
            default -> status >= 500 ? "Ocurrió un error inesperado" : "No se pudo procesar la solicitud";
        };
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
        }
        return null;
    }

    private static String constraintName(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException violation) return violation.getConstraintName();
        }
        return null;
    }
}
