package com.fooda.api.core.domain.stock;

public record Shortage(long productionId, long packId, int available, int requested) {
}
