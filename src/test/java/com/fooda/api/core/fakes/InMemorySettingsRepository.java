package com.fooda.api.core.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class InMemorySettingsRepository implements SettingsRepository {

    private Settings current;

    @Override public Optional<Settings> find() { return Optional.ofNullable(current); }

    @Override
    public Settings save(Settings s) {
        current = new Settings(s.packPrice(), s.oilMlPerPack(), s.oilSupplyId(), s.traysPerPack(), s.stickersPerPack(),
                s.referenceOverhead(), s.regularMonths(), s.recontactDays(), s.priceRiseAlert(), s.saleReasons(),
                s.expenseTypes(), s.version() == null ? 0L : s.version() + 1);
        return current;
    }
}
