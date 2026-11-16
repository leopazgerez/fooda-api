package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SoldPacks;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.core.domain.sales.SaleType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SalePersistenceAdapter implements SaleRepository, SoldPacks {

    private static final List<String> INCOME_TYPES = Arrays.stream(SaleType.values())
            .filter(SaleRules::isIncome).map(SaleType::code).toList();

    private final SaleJpaRepository sales;
    private final SaleLineJpaRepository lines;
    private final Clock clock;

    public SalePersistenceAdapter(SaleJpaRepository sales, SaleLineJpaRepository lines, Clock clock) {
        this.sales = sales;
        this.lines = lines;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Sale add(Sale s) {
        SaleJpaEntity e = new SaleJpaEntity();
        e.date = s.date();
        e.branchId = s.branchId();
        e.customerId = s.customerId();
        e.type = s.type().code();
        e.reason = s.reason();
        e.paymentMethod = s.method().code();
        e.discount = s.discount();
        e.shipping = s.shipping();
        e.notes = s.notes();
        SaleJpaEntity saved = sales.saveAndFlush(e);
        lines.saveAll(s.lines().stream().map(l -> {
            SaleLineJpaEntity r = new SaleLineJpaEntity();
            r.saleId = saved.id;
            r.productionId = l.productionId();
            r.packId = l.packId();
            r.quantity = l.quantity();
            r.unitPrice = l.unitPrice();
            return r;
        }).toList());
        lines.flush();
        return new Sale(saved.id, s.date(), s.branchId(), s.customerId(), s.type(), s.reason(), s.method(),
                s.discount(), s.shipping(), s.notes(), s.lines());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findAll() {
        return withLines(sales.findAllByOrderById());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sale> findById(long id) {
        return sales.findById(id).map(e -> withLines(List.of(e)).get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCustomer(long customerId) {
        return sales.existsByCustomerId(customerId);
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        Instant now = clock.instant();
        lines.softDeleteBySale(id, actorId, now);
        sales.softDelete(id, actorId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> soldByProduction() {
        Map<Long, Integer> out = new HashMap<>();
        for (Object[] row : lines.soldByProduction(INCOME_TYPES)) {
            out.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    private List<Sale> withLines(List<SaleJpaEntity> es) {
        if (es.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SaleLine>> bySale = new HashMap<>();
        lines.findBySaleIdInOrderById(es.stream().map(e -> e.id).toList()).forEach(l ->
                bySale.computeIfAbsent(l.saleId, k -> new ArrayList<>())
                        .add(new SaleLine(l.productionId, l.packId, l.quantity, l.unitPrice)));
        return es.stream().map(e -> new Sale(e.id, e.date, e.branchId, e.customerId, SaleType.of(e.type), e.reason,
                PaymentMethod.of(e.paymentMethod, "method"), e.discount, e.shipping, e.notes,
                bySale.getOrDefault(e.id, List.of()))).toList();
    }
}
