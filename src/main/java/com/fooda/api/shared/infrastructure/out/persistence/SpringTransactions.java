package com.fooda.api.shared.infrastructure.out.persistence;

import com.fooda.api.shared.application.port.out.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactions implements Transactions {

    private final TransactionTemplate template;

    public SpringTransactions(PlatformTransactionManager manager) {
        this.template = new TransactionTemplate(manager);
    }

    @Override
    public void run(Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }

    @Override
    public <T> T get(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
