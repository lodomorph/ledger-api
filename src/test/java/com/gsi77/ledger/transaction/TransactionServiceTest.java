package com.gsi77.ledger.transaction;

import com.gsi77.ledger.account.Account;
import com.gsi77.ledger.account.AccountService;
import com.gsi77.ledger.account.AccountStatus;
import com.gsi77.ledger.balance.BalanceService;
import com.gsi77.ledger.exception.AccountNotActiveException;
import com.gsi77.ledger.exception.InsufficientFundsException;
import com.gsi77.ledger.transaction.dto.PostTransactionRequest;
import com.gsi77.ledger.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private AccountService accountService;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private TransactionService transactionService;

    private Account activeAccount;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        activeAccount = Account.builder()
                .id(accountId)
                .accountNumber("ACC1234567")
                .ownerName("Juan Dela Cruz")
                .currency("PHP")
                .status(AccountStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void postTransaction_credit_shouldSucceed() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(500))
                .description("Initial deposit")
                .build();

        when(accountService.findAccountOrThrow(accountId)).thenReturn(activeAccount);
        when(transactionRepository.computeBalance(accountId)).thenReturn(BigDecimal.ZERO);

        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(500))
                .balanceAfter(BigDecimal.valueOf(500))
                .build();
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

        TransactionResponse expectedResponse = TransactionResponse.builder()
                .id(saved.getId())
                .accountId(accountId)
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(500))
                .balanceAfter(BigDecimal.valueOf(500))
                .build();
        when(transactionMapper.toResponse(saved)).thenReturn(expectedResponse);

        TransactionResponse result = transactionService.postTransaction(accountId, request);

        assertThat(result.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(balanceService).evictBalance(accountId);
    }

    @Test
    void postTransaction_debit_withinBalance_shouldSucceed() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(200))
                .description("Withdrawal")
                .build();

        when(accountService.findAccountOrThrow(accountId)).thenReturn(activeAccount);
        when(transactionRepository.computeBalance(accountId)).thenReturn(BigDecimal.valueOf(500));

        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID())
                .account(activeAccount)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(200))
                .balanceAfter(BigDecimal.valueOf(300))
                .build();
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

        TransactionResponse expectedResponse = TransactionResponse.builder()
                .id(saved.getId())
                .accountId(accountId)
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(200))
                .balanceAfter(BigDecimal.valueOf(300))
                .build();
        when(transactionMapper.toResponse(saved)).thenReturn(expectedResponse);

        TransactionResponse result = transactionService.postTransaction(accountId, request);

        assertThat(result.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(result.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void postTransaction_debit_insufficientFunds_shouldThrow() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(1000))
                .build();

        when(accountService.findAccountOrThrow(accountId)).thenReturn(activeAccount);
        when(transactionRepository.computeBalance(accountId)).thenReturn(BigDecimal.valueOf(500));

        assertThatThrownBy(() -> transactionService.postTransaction(accountId, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void postTransaction_frozenAccount_shouldThrow() {
        activeAccount.setStatus(AccountStatus.FROZEN);
        PostTransactionRequest request = PostTransactionRequest.builder()
                .type(TransactionType.CREDIT)
                .amount(BigDecimal.valueOf(100))
                .build();

        when(accountService.findAccountOrThrow(accountId)).thenReturn(activeAccount);

        assertThatThrownBy(() -> transactionService.postTransaction(accountId, request))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void postTransaction_closedAccount_shouldThrow() {
        activeAccount.setStatus(AccountStatus.CLOSED);
        PostTransactionRequest request = PostTransactionRequest.builder()
                .type(TransactionType.DEBIT)
                .amount(BigDecimal.valueOf(50))
                .build();

        when(accountService.findAccountOrThrow(accountId)).thenReturn(activeAccount);

        assertThatThrownBy(() -> transactionService.postTransaction(accountId, request))
                .isInstanceOf(AccountNotActiveException.class);
    }
}
