package com.fooda.api.stock.application.port.in;

import com.fooda.api.core.domain.stock.Transfer;

import java.util.List;

public record TransferView(Transfer transfer, String origin, String destination, int packs, List<LineView> lines) {

    public record LineView(long productionId, String lot, long packId, String pack, int quantity) {
    }
}
