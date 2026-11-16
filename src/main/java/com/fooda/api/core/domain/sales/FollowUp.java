package com.fooda.api.core.domain.sales;

import java.time.LocalDate;
import java.util.List;

public record FollowUp(Customer customer, LocalDate lastPurchase, long daysWithoutPurchase, List<Long> packIds) {
}
