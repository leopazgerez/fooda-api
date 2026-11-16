package com.fooda.api.sales.application.service;

import com.fooda.api.core.application.port.out.CustomerRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.sales.Customer;
import com.fooda.api.core.domain.sales.CustomerRules;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.sales.application.port.in.CustomerInput;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.CustomerView;
import com.fooda.api.sales.application.port.in.FollowUpsView;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomerService implements CustomerUseCase {

    private static final Comparator<CustomerView> BY_ID = Comparator.comparingLong(v -> v.customer().id());
    private static final Map<String, Comparator<CustomerView>> SORTABLE = Map.of(
            "fullName", Comparator.comparing(CustomerView::fullName, String.CASE_INSENSITIVE_ORDER),
            "id", BY_ID);

    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final PackRepository packs;
    private final SettingsRepository settings;
    private final BusinessCalendar calendar;
    private final StockLock stockLock;
    private final Transactions tx;

    public CustomerService(CustomerRepository customers, SaleRepository sales, PackRepository packs,
                           SettingsRepository settings, StockLock stockLock, BusinessCalendar calendar,
                           Transactions tx) {
        this.customers = customers;
        this.sales = sales;
        this.packs = packs;
        this.settings = settings;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<CustomerView> list(ListQuery q) {
        Map<Long, List<Sale>> all = byCustomer(sales.findAll());
        Settings s = settings.require();
        LocalDate today = calendar.today();
        return Listing.page(customers.findAll().stream().map(c -> view(c, all, s, today)).toList(), q, SORTABLE,
                "fullName", "asc", BY_ID);
    }

    @Override
    public CustomerView get(long id) {
        return view(find(id), byCustomer(sales.findAll()), settings.require(), calendar.today());
    }

    @Override
    public CustomerView create(CustomerInput in) {
        return tx.get(() -> {
            Customer c = customers.save(new Customer(null, in.firstName().or(null), in.lastName().or(null),
                    in.phone().or(null), in.address().or(null), in.birthday().or(null), in.notes().or(null), null));
            return view(c, Map.of(), settings.require(), calendar.today()); // a new customer has no sales
        });
    }

    @Override
    public CustomerView update(long id, CustomerInput in) {
        return tx.get(() -> {
            Customer c = find(id);
            Customer saved = customers.save(new Customer(id, in.firstName().or(c.firstName()),
                    in.lastName().or(c.lastName()), in.phone().or(c.phone()), in.address().or(c.address()),
                    in.birthday().or(c.birthday()), in.notes().or(c.notes()), in.version().or(c.version())));
            return view(saved, byCustomer(sales.findAll()), settings.require(), calendar.today());
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock();
            find(id);
            if (sales.existsByCustomer(id)) {
                throw new ConflictException(null, "El cliente tiene ventas; no se puede borrar");
            }
            customers.softDelete(id, actorId);
        });
    }

    @Override
    public FollowUpsView followUps() {
        Settings s = settings.require();
        LocalDate today = calendar.today();
        List<Sale> sold = sales.findAll();
        Map<Long, List<Sale>> all = byCustomer(sold);
        List<Customer> cs = customers.findAll();
        Map<Long, String> names = packs.findAll().stream().collect(Collectors.toMap(Pack::id, Pack::name));
        var followUps = CustomerRules.followUps(cs, sold, today, s.recontactDays()).stream()
                .map(f -> new FollowUpsView.FollowUpView(view(f.customer(), all, s, today), f.lastPurchase(),
                        f.daysWithoutPurchase(), f.packIds(),
                        f.packIds().stream().map(p -> names.getOrDefault(p, "—")).toList()))
                .toList();
        var birthdays = CustomerRules.birthdaysToday(cs, today).stream().map(c -> view(c, all, s, today)).toList();
        return new FollowUpsView(followUps, birthdays);
    }

    private Customer find(long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    /** Income sales by customer, grouped once per request. */
    private static Map<Long, List<Sale>> byCustomer(List<Sale> all) {
        return all.stream().filter(x -> x.customerId() != null && SaleRules.isIncome(x.type()))
                .collect(Collectors.groupingBy(Sale::customerId));
    }

    private static CustomerView view(Customer c, Map<Long, List<Sale>> byCustomer, Settings s, LocalDate today) {
        List<Sale> mine = byCustomer.getOrDefault(c.id(), List.of());
        int packs = mine.stream().mapToInt(x -> SaleRules.packs(x.lines())).sum();
        BigDecimal total = mine.stream().map(SaleRules::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate last = mine.stream().map(Sale::date).max(Comparator.naturalOrder()).orElse(null);
        return new CustomerView(c, c.fullName(), CustomerRules.isRegular(c.id(), mine, today, s.regularMonths()), last,
                packs, total);
    }
}
