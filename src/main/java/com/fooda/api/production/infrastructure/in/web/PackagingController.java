package com.fooda.api.production.infrastructure.in.web;

import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@link PackagingStatus} is already the response shape (tray, sticker). */
@RestController
@RequestMapping("/api/packaging")
public class PackagingController {

    private final PurchaseUseCase purchases;

    public PackagingController(PurchaseUseCase purchases) {
        this.purchases = purchases;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public PackagingStatus get() {
        return purchases.packaging();
    }
}
