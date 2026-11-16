package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.SettingsUseCase;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    public record SettingsResponse(BigDecimal packPrice, BigDecimal oilMlPerPack, Long oilSupplyId, int traysPerPack,
                                   int stickersPerPack, BigDecimal referenceOverhead, int regularMonths,
                                   int recontactDays, BigDecimal priceRiseAlert, List<String> saleReasons,
                                   List<String> expenseTypes, Long version) {
        static SettingsResponse from(Settings s) {
            return new SettingsResponse(s.packPrice(), s.oilMlPerPack(), s.oilSupplyId(), s.traysPerPack(),
                    s.stickersPerPack(), s.referenceOverhead(), s.regularMonths(), s.recontactDays(),
                    s.priceRiseAlert(), s.saleReasons(), s.expenseTypes(), s.version());
        }
    }

    private final SettingsUseCase settings;

    public SettingsController(SettingsUseCase settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public SettingsResponse get() {
        return SettingsResponse.from(settings.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    public SettingsResponse replace(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return SettingsResponse.from(settings.replace(new Settings(b.decimal("packPrice").value(),
                b.decimal("oilMlPerPack").value(), b.id("oilSupplyId").value(), Bodies.intOr0(b, "traysPerPack"),
                Bodies.intOr0(b, "stickersPerPack"), b.decimal("referenceOverhead").value(),
                Bodies.intOr0(b, "regularMonths"), Bodies.intOr0(b, "recontactDays"),
                b.decimal("priceRiseAlert").value(), Bodies.strings(b.list("saleReasons").value(), "saleReasons"),
                Bodies.strings(b.list("expenseTypes").value(), "expenseTypes"), b.id("version").value())));
    }
}
