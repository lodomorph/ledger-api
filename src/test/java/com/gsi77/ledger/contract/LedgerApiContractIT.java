package com.gsi77.ledger.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Provider("ledger-api")
@PactBroker
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class LedgerApiContractIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("ledger_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("an account with id 00000000-0000-0000-0000-000000000001 exists")
    void accountExists() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                "INSERT INTO accounts (id, account_number, owner_name, currency, status, created_at) " +
                "VALUES (?::uuid, ?, ?, ?, ?, NOW())",
                "00000000-0000-0000-0000-000000000001", "ACC0000001", "Juan dela Cruz", "PHP", "ACTIVE"
        );
    }

    @State("no account with id 00000000-0000-0000-0000-000000000999 exists")
    void accountDoesNotExist() {
        jdbcTemplate.update("DELETE FROM transactions WHERE account_id = '00000000-0000-0000-0000-000000000999'::uuid");
        jdbcTemplate.update("DELETE FROM accounts WHERE id = '00000000-0000-0000-0000-000000000999'::uuid");
    }

    @State("an account with id 00000000-0000-0000-0000-000000000001 has transactions")
    void accountExistsWithTransactions() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                "INSERT INTO accounts (id, account_number, owner_name, currency, status, created_at) " +
                "VALUES (?::uuid, ?, ?, ?, ?, NOW())",
                "00000000-0000-0000-0000-000000000001", "ACC0000001", "Juan dela Cruz", "PHP", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO transactions (id, account_id, type, amount, description, balance_after, created_at) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, NOW())",
                "00000000-0000-0000-0000-000000000010",
                "00000000-0000-0000-0000-000000000001",
                "CREDIT",
                new java.math.BigDecimal("250.00"),
                "Salary payment",
                new java.math.BigDecimal("1500.00")
        );
    }

    @State("the ledger service is available")
    void serviceAvailable() {
        // No setup needed — the running Spring Boot app with Testcontainers is sufficient
    }
}
