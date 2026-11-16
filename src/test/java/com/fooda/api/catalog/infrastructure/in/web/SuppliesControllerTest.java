package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.SupplyUseCase;
import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuppliesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class SuppliesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SupplyUseCase supplies;
    @MockitoBean UserRepository users;

    private static final String BODY = "{\"name\":\"POLLO\",\"unit\":\"%s\",\"supplierId\":1,\"content\":1000}";

    @Test
    void unknownUnitIs400() throws Exception {
        mvc.perform(post("/api/supplies").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content(BODY.formatted("kg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("unit"));
    }

    @Test
    void conflictMapsTo409WithField() throws Exception {
        given(supplies.create(any())).willThrow(new ConflictException("name", "POLLO ya existe"));
        mvc.perform(post("/api/supplies").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content(BODY.formatted("g")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("name"));
    }
}
