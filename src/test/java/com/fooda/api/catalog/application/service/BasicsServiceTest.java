package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.catalog.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasicsServiceTest {

    private final InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
    private final InMemoryBranchRepository branchRepo = new InMemoryBranchRepository();
    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final DirectTransactions tx = new DirectTransactions();
    private final SupplierUseCase suppliers = new SupplierService(supplierRepo, supplyRepo, tx);
    private final BranchUseCase branches = new BranchService(branchRepo, tx);
    private final ReserveFundUseCase funds = new ReserveFundService(new InMemoryReserveFundRepository(), tx);
    private final SettingsUseCase settings = new SettingsService(settingsRepo, supplyRepo, tx);

    private static Branch branch(String name, boolean kitchen) {
        return new Branch(null, name, "", 10, 1, 1, new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, kitchen, null);
    }

    private static Settings settings(Long oilSupplyId) {
        return new Settings(BigDecimal.TEN, BigDecimal.ONE, oilSupplyId, 1, 1, BigDecimal.ONE, 1, 1,
                BigDecimal.ONE, List.of(), List.of("fijo"), null);
    }

    @Test void patchClearsOptionalAndRejectsEmptyRequired() {
        Supplier s = suppliers.create(new Supplier(null, "Súper", "jueves", 1, null));
        Supplier cleared = suppliers.update(s.id(), new SupplierChanges(Field.absent(), Field.of(null), Field.absent(), Field.absent()));
        assertThat(cleared.purchaseDay()).isNull();
        assertThat(cleared.name()).isEqualTo("Súper");
        assertThatThrownBy(() -> suppliers.update(s.id(), new SupplierChanges(Field.of(""), Field.absent(), Field.absent(), Field.absent())))
                .isInstanceOf(ValidationException.class).extracting("field").isEqualTo("name");
    }

    @Test void supplierWithSuppliesCannotBeDeleted() {
        Supplier s = suppliers.create(new Supplier(null, "Súper", null, 1, null));
        supplyRepo.save(new Supply(null, "Harina", Unit.G, s.id(), null, BigDecimal.TEN, null, null, null, true, null));
        assertThatThrownBy(() -> suppliers.delete(s.id(), 1L)).isInstanceOf(ConflictException.class);
        assertThat(suppliers.get(s.id())).isNotNull();
    }

    @Test void kitchenCannotBeDeletedNorDuplicated() {
        Branch k = branches.create(branch("Cocina", true));
        assertThatThrownBy(() -> branches.delete(k.id(), 1L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> branches.create(branch("Otra", true)))
                .isInstanceOf(ConflictException.class).extracting("field").isEqualTo("kitchen");
        Branch other = branches.create(branch("Otra", false));
        assertThatThrownBy(() -> branches.update(other.id(), new BranchChanges(Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.of(true), Field.absent())))
                .isInstanceOf(ConflictException.class).extracting("field").isEqualTo("kitchen");
        assertThat(branches.update(k.id(), new BranchChanges(Field.of("Cocina 2"), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.of(true), Field.absent())).name()).isEqualTo("Cocina 2");
    }

    @Test void suppliersListedBySortOrder() {
        suppliers.create(new Supplier(null, "B", null, 2, null));
        suppliers.create(new Supplier(null, "A", null, 1, null));
        var page = suppliers.list(new ListQuery(0, 10, null, null));
        assertThat(page.items()).extracting(Supplier::name).containsExactly("A", "B");
    }

    @Test void reserveFundsAreAlwaysDeletable() {
        ReserveFund f = funds.create(new ReserveFund(null, "Fondo", BigDecimal.ONE, null));
        funds.delete(f.id(), 1L);
        assertThatThrownBy(() -> funds.get(f.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void settingsRejectUnknownOilSupply() {
        assertThatThrownBy(() -> settings.replace(settings(99L)))
                .isInstanceOf(ValidationException.class).extracting("field").isEqualTo("oilSupplyId");
    }

    @Test void settingsReplaceThenGet() {
        settings.replace(settings(null));
        assertThat(settings.get().packPrice()).isEqualByComparingTo("10");
    }

    @Test void missingSettingsIs404() {
        assertThatThrownBy(settings::get).isInstanceOf(ResourceNotFoundException.class);
    }
}
