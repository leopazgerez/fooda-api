package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.PackUseCase;
import com.fooda.api.catalog.application.port.in.PackView;
import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PacksController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class PacksControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PackUseCase packs;
    @MockitoBean UserRepository users;

    @Test
    void listSerializesCostBlock() throws Exception {
        var version = new PackVersion(1L, 3L, LocalDate.of(2026, 1, 1),
                List.of(new MenuSlot(1, 9), new MenuSlot(2, 9), new MenuSlot(3, 9), new MenuSlot(4, 9),
                        new MenuSlot(5, 9)));
        var view = new PackView(new Pack(3L, 1, "Pack 1", true, null, 0L), new BigDecimal("1000"),
                new PackView.CurrentVersion(version, List.of(new PackView.MenuView(1, 9, "Milanesa", BigDecimal.TEN))),
                new PackView.CostView(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        new BigDecimal("42")),
                new BigDecimal("958"), new BigDecimal("0.958"));
        given(packs.list()).willReturn(List.of(view));
        mvc.perform(get("/api/packs").with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].cost.total").value(42))
                .andExpect(jsonPath("$.[0].currentVersion.menus[0].dish").value("Milanesa"));
    }
}
