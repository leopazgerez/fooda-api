package com.fooda.api.shared.infrastructure.in.web;

import com.fooda.api.config.TimeConfig;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErrorHandlingTest.Thrower.class)
@Import({GlobalExceptionHandler.class, TimeConfig.class, ErrorHandlingTest.Thrower.class})
class ErrorHandlingTest {

    @RestController
    static class Thrower {
        @GetMapping("/t/validation")
        void validation() {
            throw new ValidationException("email", "x");
        }

        @GetMapping("/t/conflict")
        void conflict() {
            throw new ConflictException("email", "x");
        }

        @GetMapping("/t/notfound")
        void notFound() {
            throw new ResourceNotFoundException("no está");
        }

        @GetMapping("/t/boom")
        void boom() {
            throw new RuntimeException("secret detail");
        }

        @GetMapping("/t/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("dup", new ConstraintViolationException(
                    "dup", new SQLException("duplicate key", "23505"), "app_user_email_key"));
        }

        @PostMapping("/t/body")
        void body(@RequestBody Map<String, Object> body) {
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void validationIs400WithField() throws Exception {
        mvc.perform(get("/t/validation").with(jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void conflictIs409WithField() throws Exception {
        mvc.perform(get("/t/conflict").with(jwt()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void notFoundIs404() throws Exception {
        mvc.perform(get("/t/notfound").with(jwt()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("no está"));
    }

    @Test
    void unexpectedIs500WithGenericMessage() throws Exception {
        mvc.perform(get("/t/boom").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado"));
    }

    @Test
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post("/t/body").with(jwt()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es válido"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        mvc.perform(get("/t/duplicate").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"))
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con ese email"));
    }
}
