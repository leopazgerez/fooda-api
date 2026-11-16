package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.SupplierUseCase;
import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuppliersController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class SuppliersControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SupplierUseCase suppliers;
    @MockitoBean UserRepository users;

    @Test
    void readOnlyCannotCreate() throws Exception {
        mvc.perform(post("/api/suppliers").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_READ")))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(suppliers.create(any())).willReturn(new Supplier(7L, "Granja", null, 0, 0L));
        mvc.perform(post("/api/suppliers").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Granja\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/suppliers/7")))
                .andExpect(jsonPath("$.name").value("Granja"));
    }

    @Test
    void patchPassesPresenceToUseCase() throws Exception {
        given(suppliers.update(eq(7L), any())).willReturn(new Supplier(7L, "Granja", null, 0, 1L));
        mvc.perform(patch("/api/suppliers/7").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content("{\"purchaseDay\":null}"))
                .andExpect(status().isOk());
        verify(suppliers).update(eq(7L), argThat(c ->
                c.purchaseDay().present() && c.purchaseDay().value() == null && !c.name().present()));
    }

    @Test
    void deleteIs204AndPassesActor() throws Exception {
        mvc.perform(delete("/api/suppliers/7").with(jwt().jwt(j -> j.subject("5"))
                        .authorities(new SimpleGrantedAuthority("CATALOG_WRITE"))))
                .andExpect(status().isNoContent());
        verify(suppliers).delete(7L, 5L);
    }
}
