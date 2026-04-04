package com.gsi77.ledger.account;

import com.gsi77.ledger.account.dto.CreateAccountRequest;
import com.gsi77.ledger.transaction.TransactionType;
import com.gsi77.ledger.transaction.dto.PostTransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AccountControllerIT {

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

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAccount_shouldReturn201() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Maria Santos")
                .currency("PHP")
                .build();

        ResponseEntity<Map> response = restTemplate.postForEntity("/accounts", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
        assertThat(response.getBody().get("ownerName")).isEqualTo("Maria Santos");
        assertThat(response.getBody().get("accountNumber")).asString().matches("ACC\\d{7}");
    }

    @Test
    void getAccountById_shouldReturn200() {
        // Create account first
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Pedro Reyes")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String id = (String) createResponse.getBody().get("id");

        ResponseEntity<Map> response = restTemplate.getForEntity("/accounts/" + id, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("ownerName")).isEqualTo("Pedro Reyes");
    }

    @Test
    void getAccountByNumber_shouldReturn200() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Ana Cruz")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String accountNumber = (String) createResponse.getBody().get("accountNumber");

        ResponseEntity<Map> response = restTemplate.getForEntity("/accounts/number/" + accountNumber, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accountNumber")).isEqualTo(accountNumber);
    }

    @Test
    void freezeAccount_thenPostTransaction_shouldReturn409() {
        // Create and freeze
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Frozen User")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String id = (String) createResponse.getBody().get("id");

        restTemplate.exchange("/accounts/" + id + "/freeze", HttpMethod.PATCH, HttpEntity.EMPTY, Map.class);

        // Try to post transaction on frozen account
        PostTransactionRequest txRequest = PostTransactionRequest.builder()
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(100))
                .build();
        ResponseEntity<Map> txResponse = restTemplate.postForEntity(
                "/accounts/" + id + "/transactions", txRequest, Map.class);

        assertThat(txResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postCredit_shouldReturn201AndIncreaseBalance() {
        // Create account
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Credit User")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String id = (String) createResponse.getBody().get("id");

        // Post credit
        PostTransactionRequest txRequest = PostTransactionRequest.builder()
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(500.00))
                .description("Salary")
                .build();
        ResponseEntity<Map> txResponse = restTemplate.postForEntity(
                "/accounts/" + id + "/transactions", txRequest, Map.class);

        assertThat(txResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Check balance
        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                "/accounts/" + id + "/balance", Map.class);
        assertThat(balanceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BigDecimal(balanceResponse.getBody().get("balance").toString()))
                .isEqualByComparingTo(BigDecimal.valueOf(500.00));
    }

    @Test
    void postDebit_withinBalance_shouldReturn201() {
        // Create and fund account
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Debit User")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String id = (String) createResponse.getBody().get("id");

        PostTransactionRequest credit = PostTransactionRequest.builder()
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(1000))
                .build();
        restTemplate.postForEntity("/accounts/" + id + "/transactions", credit, Map.class);

        // Post debit
        PostTransactionRequest debit = PostTransactionRequest.builder()
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(300))
                .build();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/accounts/" + id + "/transactions", debit, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void postDebit_exceedingBalance_shouldReturn422() {
        // Create account with no funds
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Poor User")
                .build();
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/accounts", request, Map.class);
        String id = (String) createResponse.getBody().get("id");

        PostTransactionRequest debit = PostTransactionRequest.builder()
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(100))
                .build();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/accounts/" + id + "/transactions", debit, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
