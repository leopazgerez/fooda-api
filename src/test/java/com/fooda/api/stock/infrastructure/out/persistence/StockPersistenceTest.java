package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import com.fooda.api.support.Rows;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StockPersistenceAdapter.class, TransferPersistenceAdapter.class, JdbcStockLock.class,
        JpaAuditingConfig.class, TimeConfig.class, TestcontainersConfiguration.class})
class StockPersistenceTest {

    @Autowired StockPersistenceAdapter movements;
    @Autowired TransferPersistenceAdapter transfers;
    @Autowired JdbcStockLock lock;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long actor;
    long kitchen;
    long shop;
    long pack;
    long lot;
    long otherLot;

    private void reload() {
        em.flush();
        em.clear();
    }

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
        kitchen = Rows.branch(jdbc, actor);
        shop = Rows.branch(jdbc, actor);
        pack = Rows.pack(jdbc, actor);
        lot = Rows.production(jdbc, actor);
        otherLot = Rows.production(jdbc, actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    @Test
    void transferWithLinesAndSourceProduction() {
        Transfer t = transfers.add(new Transfer(null, LocalDate.of(2026, 3, 5), kitchen, shop, "nota", lot,
                List.of(new TransferLine(lot, pack, 4), new TransferLine(otherLot, pack, 2))));
        transfers.add(new Transfer(null, LocalDate.of(2026, 3, 6), kitchen, shop, null, null,
                List.of(new TransferLine(otherLot, pack, 1))));
        reload();

        assertThat(t.id()).isNotNull();
        List<Transfer> bySource = transfers.findBySourceProduction(lot);
        assertThat(bySource).hasSize(1);
        assertThat(bySource.get(0).lines()).containsExactly(new TransferLine(lot, pack, 4), new TransferLine(otherLot, pack, 2));
        assertThat(bySource.get(0).notes()).isEqualTo("nota");
        assertThat(transfers.findContainingProduction(lot)).hasSize(1);
        assertThat(transfers.findContainingProduction(otherLot)).hasSize(2);

        transfers.softDelete(t.id(), actor);
        reload();
        assertThat(transfers.findContainingProduction(lot)).isEmpty();
        assertThat(transfers.findBySourceProduction(lot)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from transfer_line where deleted_by = ?", Integer.class, actor)).isEqualTo(2);
    }

    @Test
    void movementsByProductionIgnoreDeleted() {
        StockMovement a = movements.add(new StockMovement(null, LocalDate.of(2026, 3, 1), kitchen, lot, pack, 10, MovementType.PRODUCTION, null, null));
        StockMovement b = movements.add(new StockMovement(null, LocalDate.of(2026, 3, 2), kitchen, lot, pack, -2, MovementType.ADJUSTMENT, 9L, "merma"));
        movements.add(new StockMovement(null, LocalDate.of(2026, 3, 2), kitchen, otherLot, pack, 1, MovementType.PRODUCTION, null, null));
        reload();

        assertThat(movements.findByProduction(lot)).extracting(StockMovement::id).containsExactly(a.id(), b.id());
        assertThat(movements.findByProduction(lot).get(1).reason()).isEqualTo("merma");

        movements.softDelete(a.id(), actor);
        reload();
        assertThat(movements.findByProduction(lot)).extracting(StockMovement::id).containsExactly(b.id());
        assertThat(jdbc.queryForObject("select deleted_by from stock_movement where stock_movement_id = ?", Long.class, a.id())).isEqualTo(actor);
    }

    @Test
    void packAndBranchUsageIgnoresDeletedMovements() {
        long otherPack = Rows.pack(jdbc, actor);
        StockMovement m = movements.add(new StockMovement(null, LocalDate.of(2026, 3, 1), shop, lot, pack, 3,
                MovementType.ADJUSTMENT, null, "x"));
        reload();
        assertThat(movements.isPackUsed(pack)).isTrue();
        assertThat(movements.isBranchUsed(shop)).isTrue();
        assertThat(movements.isPackUsed(otherPack)).isFalse();
        assertThat(movements.isBranchUsed(kitchen)).isFalse();

        movements.softDelete(m.id(), actor);
        reload();
        assertThat(movements.isPackUsed(pack)).isFalse();
        assertThat(movements.isBranchUsed(shop)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void advisoryLockOutsideTransactionFails() {
        assertThatThrownBy(lock::lock).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void advisoryLockRunsInsideTransaction() {
        lock.lock();

        assertThat(jdbc.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and objid = 7001"
                + " and pid = pg_backend_pid() and granted", Integer.class)).isEqualTo(1);
    }
}
