package com.gsi77.ledger.account;

import com.gsi77.ledger.account.dto.AccountResponse;
import com.gsi77.ledger.account.dto.CreateAccountRequest;
import com.gsi77.ledger.balance.BalanceService;
import com.gsi77.ledger.exception.AccountNotActiveException;
import com.gsi77.ledger.exception.AccountNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;
    private AccountResponse sampleResponse;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        sampleAccount = Account.builder()
                .id(accountId)
                .accountNumber("ACC1234567")
                .ownerName("Juan Dela Cruz")
                .currency("PHP")
                .status(AccountStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .build();

        sampleResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("ACC1234567")
                .ownerName("Juan Dela Cruz")
                .currency("PHP")
                .status(AccountStatus.ACTIVE)
                .currentBalance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void createAccount_shouldSaveAndReturnResponse() {
        CreateAccountRequest request = CreateAccountRequest.builder()
                .ownerName("Juan Dela Cruz")
                .currency("PHP")
                .build();

        when(accountNumberGenerator.generate()).thenReturn("ACC1234567");
        when(accountRepository.save(any(Account.class))).thenReturn(sampleAccount);
        when(accountMapper.toResponse(eq(sampleAccount), eq(BigDecimal.ZERO))).thenReturn(sampleResponse);

        AccountResponse result = accountService.createAccount(request);

        assertThat(result.getOwnerName()).isEqualTo("Juan Dela Cruz");
        assertThat(result.getAccountNumber()).isEqualTo("ACC1234567");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void getAccountById_found_shouldReturnResponse() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));
        when(balanceService.getBalance(accountId)).thenReturn(BigDecimal.valueOf(1000));
        when(accountMapper.toResponse(sampleAccount, BigDecimal.valueOf(1000))).thenReturn(sampleResponse);

        AccountResponse result = accountService.getAccountById(accountId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(accountId);
    }

    @Test
    void getAccountById_notFound_shouldThrow() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void freezeAccount_activeAccount_shouldFreeze() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(sampleAccount);
        when(balanceService.getBalance(accountId)).thenReturn(BigDecimal.ZERO);
        when(accountMapper.toResponse(any(), any())).thenReturn(sampleResponse);

        AccountResponse result = accountService.freezeAccount(accountId);

        assertThat(result).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void freezeAccount_frozenAccount_shouldThrow() {
        sampleAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));

        assertThatThrownBy(() -> accountService.freezeAccount(accountId))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void closeAccount_activeAccount_shouldClose() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(sampleAccount);
        when(balanceService.getBalance(accountId)).thenReturn(BigDecimal.ZERO);
        when(accountMapper.toResponse(any(), any())).thenReturn(sampleResponse);

        AccountResponse result = accountService.closeAccount(accountId);

        assertThat(result).isNotNull();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void closeAccount_closedAccount_shouldThrow() {
        sampleAccount.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sampleAccount));

        assertThatThrownBy(() -> accountService.closeAccount(accountId))
                .isInstanceOf(AccountNotActiveException.class);
    }
}
