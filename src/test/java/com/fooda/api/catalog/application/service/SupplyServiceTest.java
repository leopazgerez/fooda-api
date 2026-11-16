package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.*;
import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplyServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
    private final InMemoryDishRepository dishRepo = new InMemoryDishRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final BusinessCalendar calendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
    private final SupplyUseCase service = new SupplyService(supplyRepo, supplierRepo, dishRepo, settingsRepo,
            calendar, new DirectTransactions());
    private final long supplierId = supplierRepo.save(new Supplier(null, "Súper", null, 1, null)).id();

    private NewSupply newSupply(String name) {
        return new NewSupply(name, Unit.G, supplierId, null, new BigDecimal("1000"), null, null, null, null);
    }

    private NewSupply composite(String name, Long... parts) {
        return new NewSupply(name, Unit.G, supplierId, null, BigDecimal.ONE, null, null,
                new Composite(CompositeRule.AVERAGE, List.of(parts)), null);
    }

    private static SupplyChanges noChanges() {
        return new SupplyChanges(Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent());
    }

    @Test void listShowsCurrentUnitCostAndPrice() {
        long id = service.create(new NewSupply("Pollo", Unit.G, supplierId, null, new BigDecimal("2900"), null, null,
                null, new BigDecimal("12100"))).supply().id();
        var v = service.list(new ListQuery(0, 10, null, null)).items().get(0);
        assertThat(v.supply().id()).isEqualTo(id);
        assertThat(v.unitCost()).isCloseTo(new BigDecimal("4.1724"), org.assertj.core.data.Offset.offset(new BigDecimal("0.0001")));
        assertThat(v.packagePrice()).isEqualByComparingTo("12100");
        assertThat(v.supplierName()).isEqualTo("Súper");
    }

    @Test void duplicateNameIgnoringCaseIs409() {
        service.create(newSupply("Pollo"));
        assertThatThrownBy(() -> service.create(newSupply("POLLO")))
                .isInstanceOf(ConflictException.class).hasMessage("POLLO ya existe").extracting("field").isEqualTo("name");
    }

    @Test void nameFreedAfterDelete() {
        long id = service.create(newSupply("pollo")).supply().id();
        service.delete(id, 1L);
        assertThat(service.create(newSupply("Pollo")).supply().name()).isEqualTo("Pollo");
    }

    @Test void createWithPriceAddsFirstPriceDatedToday() {
        var v = new NewSupply("Pollo", Unit.G, supplierId, null, BigDecimal.TEN, null, null, null, new BigDecimal("9500"));
        long id = service.create(v).supply().id();
        var prices = service.prices(id);
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).date()).isEqualTo(TODAY);
        assertThat(prices.get(0).packagePrice()).isEqualByComparingTo("9500");
    }

    @Test void supplyUsedInRecipeOrCompositeOrAsOilCannotBeDeleted() {
        long inRecipe = service.create(newSupply("Harina")).supply().id();
        dishRepo.save(new Dish(null, "Torta", List.of(new RecipeLine(inRecipe, BigDecimal.ONE, Unit.G, BigDecimal.ONE)), null));
        long part = service.create(newSupply("Tomate")).supply().id();
        service.create(composite("Salsa", part));
        long oil = service.create(newSupply("Aceite")).supply().id();
        settingsRepo.save(new Settings(BigDecimal.TEN, BigDecimal.ONE, oil, 1, 1, BigDecimal.ONE, 1, 1,
                BigDecimal.ONE, List.of(), List.of("fijo"), null));
        for (long id : new long[]{inRecipe, part, oil}) {
            assertThatThrownBy(() -> service.delete(id, 1L)).isInstanceOf(ConflictException.class);
            assertThat(service.get(id)).isNotNull();
        }
    }

    @Test void compositeOfCompositeIs400() {
        long part = service.create(newSupply("Tomate")).supply().id();
        long sauce = service.create(composite("Salsa", part)).supply().id();
        assertThatThrownBy(() -> service.create(composite("Mega", sauce)))
                .isInstanceOf(ValidationException.class).extracting("field").isEqualTo("composite");
    }

    @Test void supplyThatIsAPartCannotBecomeComposite() {
        long part = service.create(newSupply("Tomate")).supply().id();
        long other = service.create(newSupply("Cebolla")).supply().id();
        service.create(composite("Salsa", part));
        var c = new SupplyChanges(Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.of(new Composite(CompositeRule.MAX, List.of(other))), Field.absent());
        assertThatThrownBy(() -> service.update(part, c))
                .isInstanceOf(ValidationException.class).hasMessage("Las partes de un compuesto no pueden ser compuestas");
    }

    @Test void missingSupplierOrPartIs404() {
        assertThatThrownBy(() -> service.create(new NewSupply("X", Unit.G, 999, null, BigDecimal.ONE, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Proveedor no encontrado");
        assertThatThrownBy(() -> service.create(composite("Y", 999L)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Insumo no encontrado");
    }

    @Test void packagingTypeAllowedOnlyOnce() {
        service.create(new NewSupply("Bandeja", Unit.U, supplierId, null, BigDecimal.ONE, null, PackagingType.TRAY, null, null));
        assertThatThrownBy(() -> service.create(new NewSupply("Bandeja2", Unit.U, supplierId, null, BigDecimal.ONE, null,
                PackagingType.TRAY, null, null)))
                .isInstanceOf(ConflictException.class).extracting("field").isEqualTo("packagingType");
    }

    @Test void patchClearsGramsPerUnit() {
        long id = service.create(new NewSupply("Huevo", Unit.G, supplierId, null, BigDecimal.TEN, new BigDecimal("60"),
                null, null, null)).supply().id();
        var c = new SupplyChanges(Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent(),
                Field.of(null), Field.absent(), Field.absent(), Field.absent());
        assertThat(service.update(id, c).supply().gramsPerUnit()).isNull();
        assertThat(service.update(id, noChanges()).supply().name()).isEqualTo("Huevo");
    }

    @Test void deletingPriceChangesCurrentCost() {
        long id = service.create(newSupply("Pollo")).supply().id();
        service.addPrice(id, new BigDecimal("1000"), TODAY.minusDays(5));
        var last = service.addPrice(id, new BigDecimal("2000"), null);
        assertThat(service.get(id).unitCost()).isEqualByComparingTo("2");
        service.deletePrice(id, last.id(), 1L);
        assertThat(service.get(id).unitCost()).isEqualByComparingTo("1");
    }

    @Test void priceOfAnotherSupplyIs404() {
        long a = service.create(newSupply("A")).supply().id();
        long b = service.create(newSupply("B")).supply().id();
        var p = service.addPrice(a, BigDecimal.TEN, null);
        assertThatThrownBy(() -> service.deletePrice(b, p.id(), 1L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Precio no encontrado");
    }
}
