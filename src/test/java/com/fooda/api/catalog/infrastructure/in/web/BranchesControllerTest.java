package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.BranchUseCase;
import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BranchesController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class BranchesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean BranchUseCase branches;
    @MockitoBean UserRepository users;

    private void bad(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r, String body, String field) throws Exception {
        mvc.perform(r.with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value(field));
    }

    @Test
    void patchNullCapacityIs400() throws Exception {
        bad(patch("/api/branches/7"), "{\"capacity\":null}", "capacity");
    }

    @Test
    void patchNullShippingIs400() throws Exception {
        bad(patch("/api/branches/7"), "{\"shipping\":null}", "shipping");
    }

    @Test
    void postWithoutCapacityIs400() throws Exception {
        bad(post("/api/branches"), "{\"name\":\"Sur\"}", "capacity");
    }
}

