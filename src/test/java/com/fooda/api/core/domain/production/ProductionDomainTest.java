package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionDomainTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 5);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static void invalid(Runnable r, String field, String message) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    private static Production production(ProductionStatus s, LocalDate closedOn, List<ProductionLine> lines) {
        return new Production(null, "P1", 1, s, D, closedOn, lines, null, null, null, null);
    }

    private static Expense expense(String type, String detail, BigDecimal amount, BigDecimal packs, BigDecimal price) {
        return new Expense(null, D, type, detail, amount, packs, price, PaymentMethod.CASH, true, null, null, null,
                null);
    }

    @Test void enumCodeRoundTrip() {
        for (ProductionStatus v : ProductionStatus.values()) assertThat(ProductionStatus.of(v.code(), "f")).isEqualTo(v);
        for (PurchaseStatus v : PurchaseStatus.values()) assertThat(PurchaseStatus.of(v.code(), "f")).isEqualTo(v);
        for (MovementType v : MovementType.values()) assertThat(MovementType.of(v.code(), "f")).isEqualTo(v);
        for (PaymentMethod v : PaymentMethod.values()) assertThat(PaymentMethod.of(v.code(), "f")).isEqualTo(v);
        assertThat(MovementType.TRANSFER_IN.code()).isEqualTo("transfer_in");
        assertThat(PaymentMethod.MERCADO_PAGO.code()).isEqualTo("mercado_pago");
    }

    @Test void enumUnknownCode() {
        invalid(() -> ProductionStatus.of("x", "status"), "status", "Valor inválido");
        invalid(() -> PurchaseStatus.of(null, "status"), "status", "Valor inválido");
        invalid(() -> MovementType.of("x", "type"), "type", "Valor inválido");
        invalid(() -> PaymentMethod.of("x", "method"), "method", "Medio de pago inválido");
    }

    @Test void productionLineValidation() {
        invalid(() -> new ProductionLine(1, -1, null), "lines", "Las cantidades deben ser enteros ≥ 0");
        invalid(() -> new ProductionLine(1, 1, -1), "lines", "Las cantidades reales deben ser enteros ≥ 0");
        assertThat(new ProductionLine(1, 0, 0).actual()).isZero();
    }

    @Test void packsVsActualPacks() {
        Production p = production(ProductionStatus.PLANNED, null,
                List.of(new ProductionLine(1, 10, 8), new ProductionLine(2, 5, null)));
        assertThat(p.actualPacks()).isEqualTo(8);
        assertThat(p.packs()).isEqualTo(13);
        assertThat(p.isPlanned()).isTrue();
    }

    @Test void closedProductionRequiresClosedOn() {
        assertThatThrownBy(() -> production(ProductionStatus.CLOSED, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> production(ProductionStatus.PLANNED, D, null))
                .isInstanceOf(ValidationException.class);
        assertThat(production(ProductionStatus.CLOSED, D, null).isPlanned()).isFalse();
    }

    @Test void snapshotDefaultsAndPackLookup() {
        Settings s = new Settings(d("1"), d("10"), null, 5, 5, d("1"), 3, 30, d("10"), List.of("a"), List.of("fijo"), null);
        ProductionSnapshot e = ProductionSnapshot.empty(s);
        assertThat(e.costs()).isEmpty();
        assertThat(e.packs()).isEmpty();
        assertThat(e.settings()).isSameAs(s);
        ProductionSnapshot snap = new ProductionSnapshot(null, null, List.of(new SnapshotPack(7, 70, null)), s, null);
        Production p = new Production(null, "P", 1, ProductionStatus.PLANNED, D, null, null, snap, null, null, null);
        assertThat(p.snapshotPack(7)).get().extracting(SnapshotPack::versionId).isEqualTo(70L);
        assertThat(p.snapshotPack(8)).isEmpty();
    }

    @Test void purchaseLineValidation() {
        String m = "Revisá cantidades y precios";
        invalid(() -> new PurchaseLine(1, null, d("1")), "lines", m);
        invalid(() -> new PurchaseLine(1, d("0"), d("1")), "lines", m);
        invalid(() -> new PurchaseLine(1, d("1"), null), "lines", m);
        invalid(() -> new PurchaseLine(1, d("1"), d("-1")), "lines", m);
        assertThat(new PurchaseLine(1, d("1"), d("0")).packagePrice()).isEqualByComparingTo("0");
    }

    @Test void stockMovementZeroQuantity() {
        assertThatThrownBy(() -> new StockMovement(null, D, 1, 1, 1, 0, MovementType.SALE, null, null))
                .isInstanceOf(ValidationException.class);
        assertThat(new StockMovement(null, D, 1, 1, 1, -2, MovementType.SALE, null, null).quantity()).isEqualTo(-2);
    }

    @Test void transferValidation() {
        invalid(() -> new TransferLine(1, 1, 0), "lines", "Las cantidades deben ser enteros mayores a cero");
        invalid(() -> new Transfer(null, D, 1, 1, null, null, null), "destinationBranchId",
                "Origen y destino deben ser distintos");
        assertThat(new Transfer(null, D, 1, 2, null, null, null).lines()).isEmpty();
    }

    @Test void expenseValidation() {
        invalid(() -> expense(" ", "d", d("1"), null, null), "type", "Completá tipo");
        invalid(() -> expense("t", null, d("1"), null, null), "detail", "Completá detalle");
        invalid(() -> expense("t", "d", null, null, null), "amount", "El monto debe ser un número mayor a cero");
        invalid(() -> expense("t", "d", d("0"), null, null), "amount", "El monto debe ser un número mayor a cero");
        assertThatThrownBy(() -> expense("t", "d", d("1"), d("2"), null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> expense("t", "d", d("1"), null, d("2"))).isInstanceOf(ValidationException.class);
        assertThat(expense("t", "d", d("1"), d("2"), d("3")).packPrice()).isEqualByComparingTo("3");
    }
}
