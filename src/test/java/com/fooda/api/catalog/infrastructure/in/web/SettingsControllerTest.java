package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.SettingsUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class SettingsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SettingsUseCase settings;
    @MockitoBean UserRepository users;

    @Test
    void putNeedsSettingsWrite() throws Exception {
        mvc.perform(put("/api/settings").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_WRITE")))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
