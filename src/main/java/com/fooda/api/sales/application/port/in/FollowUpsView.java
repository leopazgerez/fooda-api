package com.fooda.api.sales.application.port.in;

import java.time.LocalDate;
import java.util.List;

public record FollowUpsView(List<FollowUpView> followUps, List<CustomerView> birthdays) {

    public record FollowUpView(CustomerView customer, LocalDate lastPurchase, long daysWithoutPurchase,
                               List<Long> packIds, List<String> packs) {
    }
}
