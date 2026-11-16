package com.fooda.api.finance.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.domain.finance.MonthClose;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.fooda.api.shared.infrastructure.out.persistence.JsonbMapper.JSON;

@Repository
public class MonthClosePersistenceAdapter implements MonthCloseRepository {

    private final MonthCloseJpaRepository repo;
    private final Clock clock;

    public MonthClosePersistenceAdapter(MonthCloseJpaRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MonthClose> find(YearMonth month) {
        return repo.findByMonth(month.toString()).map(MonthClosePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthClose> findAll() {
        return repo.findAllByOrderByMonth().stream().map(MonthClosePersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public MonthClose add(MonthClose c) {
        MonthCloseJpaEntity e = new MonthCloseJpaEntity();
        e.month = c.month().toString();
        e.overheadPerPack = c.overheadPerPack();
        e.closedOn = c.closedOn();
        e.report = JSON.writeValueAsString(ReportJson.of(c.report()));
        return toDomain(repo.saveAndFlush(e));
    }

    @Override
    @Transactional
    public void softDelete(YearMonth month, long actorId) {
        repo.softDelete(month.toString(), actorId, clock.instant());
    }

    private static MonthClose toDomain(MonthCloseJpaEntity e) {
        return new MonthClose(YearMonth.parse(e.month), e.overheadPerPack, e.closedOn,
                JSON.readValue(e.report, ReportJson.class).toDomain());
    }
}
