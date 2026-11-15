package com.fooda.api.config;

import com.fooda.api.shared.application.BusinessCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BusinessCalendar businessCalendar(Clock clock) {
        return new BusinessCalendar(clock);
    }
}
