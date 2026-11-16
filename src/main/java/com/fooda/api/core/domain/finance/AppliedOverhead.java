package com.fooda.api.core.domain.finance;

import java.math.BigDecimal;

public record AppliedOverhead(BigDecimal value, boolean provisional, boolean reference) {
}
