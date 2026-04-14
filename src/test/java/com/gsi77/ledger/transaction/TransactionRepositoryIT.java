package com.gsi77.ledger.transaction;

import com.gsi77.ledger.account.Account;
import com.gsi77.ledger.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("ledger_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        account = accountRepository.save(Account.builder()
                .accountNumber("ACC0000001")
                .ownerName("Test Owner")
                .currency("PHP")
                .build());
    }

    @Test
    void computeBalance_withNoTransactions_returnsZero() {
        BigDecimal balance = transactionRepository.computeBalance(account.getId());

        assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void computeBalance_withCreditsOnly_returnsTotalCredit() {
        saveTransaction(TransactionType.CREDIT, "500.00", "500.00");
        saveTransaction(TransactionType.CREDIT, "250.00", "750.00");

        BigDecimal balance = transactionRepository.computeBalance(account.getId());

        assertThat(balance).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    void computeBalance_withDebitsAndCredits_returnsNetBalance() {
        saveTransaction(TransactionType.CREDIT, "1000.00", "1000.00");
        saveTransaction(TransactionType.DEBIT,  "300.00",  "700.00");
        saveTransaction(TransactionType.DEBIT,  "150.00",  "550.00");

        BigDecimal balance = transactionRepository.computeBalance(account.getId());

        assertThat(balance).isEqualByComparingTo(new BigDecimal("550.00"));
    }

    @Test
    void findByAccountIdOrderByCreatedAtDesc_returnsTransactionsNewestFirst() {
        saveTransaction(TransactionType.CREDIT, "100.00", "100.00");
        saveTransaction(TransactionType.CREDIT, "200.00", "300.00");
        saveTransaction(TransactionType.DEBIT,  "50.00",  "250.00");

        List<Transaction> results = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(account.getId());

        assertThat(results).hasSize(3);
        // Each row's balanceAfter should descend in reverse insertion order
        assertThat(results.get(0).getBalanceAfter()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(results.get(1).getBalanceAfter()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(results.get(2).getBalanceAfter()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void computeBalance_doesNotLeakAcrossAccounts() {
        Account other = accountRepository.save(Account.builder()
                .accountNumber("ACC0000002")
                .ownerName("Other Owner")
                .currency("PHP")
                .build());

        saveTransaction(TransactionType.CREDIT, "999.00", "999.00");
        saveTransactionFor(other, TransactionType.CREDIT, "1.00", "1.00");

        assertThat(transactionRepository.computeBalance(account.getId()))
                .isEqualByComparingTo(new BigDecimal("999.00"));
        assertThat(transactionRepository.computeBalance(other.getId()))
                .isEqualByComparingTo(new BigDecimal("1.00"));
    }

    // --- helpers ---

    private void saveTransaction(TransactionType type, String amount, String balanceAfter) {
        saveTransactionFor(account, type, amount, balanceAfter);
    }

    private void saveTransactionFor(Account target, TransactionType type, String amount, String balanceAfter) {
        transactionRepository.save(Transaction.builder()
                .account(target)
                .type(type)
                .amount(new BigDecimal(amount))
                .balanceAfter(new BigDecimal(balanceAfter))
                .build());
    }
}
