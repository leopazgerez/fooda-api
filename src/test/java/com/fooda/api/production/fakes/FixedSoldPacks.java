package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.SoldPacks;

import java.util.*;

public class FixedSoldPacks implements SoldPacks {

    public final Map<Long, Integer> sold = new HashMap<>();

    @Override public Map<Long, Integer> soldByProduction() { return Map.copyOf(sold); }
}
