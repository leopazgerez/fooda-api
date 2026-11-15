package com.fooda.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** One container for the whole test JVM: every Spring test context (JPA slices, full app) shares it. */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:14-alpine")) {
                // Boot stops the container when a cached context is evicted; the other contexts still use it.
                // Ryuk removes it when the JVM exits.
                @Override
                public void stop() {
                }
            };

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
