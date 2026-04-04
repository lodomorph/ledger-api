package com.gsi77.ledger.account;

import com.gsi77.ledger.account.dto.AccountResponse;
import com.gsi77.ledger.account.dto.CreateAccountRequest;
import com.gsi77.ledger.balance.BalanceService;
import com.gsi77.ledger.exception.AccountNotActiveException;
import com.gsi77.ledger.exception.AccountNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountMapper accountMapper;
    private final BalanceService balanceService;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = Account.builder()
                .accountNumber(accountNumberGenerator.generate())
                .ownerName(request.getOwnerName())
                .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                .status(AccountStatus.ACTIVE)
                .build();
        account = accountRepository.save(account);
        return accountMapper.toResponse(account, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id) {
        Account account = findAccountOrThrow(id);
        BigDecimal balance = balanceService.getBalance(id);
        return accountMapper.toResponse(account, balance);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with number: " + accountNumber));
        BigDecimal balance = balanceService.getBalance(account.getId());
        return accountMapper.toResponse(account, balance);
    }

    @Transactional
    public AccountResponse freezeAccount(UUID id) {
        Account account = findAccountOrThrow(id);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Only ACTIVE accounts can be frozen");
        }
        account.setStatus(AccountStatus.FROZEN);
        account = accountRepository.save(account);
        BigDecimal balance = balanceService.getBalance(id);
        return accountMapper.toResponse(account, balance);
    }

    @Transactional
    public AccountResponse closeAccount(UUID id) {
        Account account = findAccountOrThrow(id);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountNotActiveException("Account is already closed");
        }
        account.setStatus(AccountStatus.CLOSED);
        account = accountRepository.save(account);
        BigDecimal balance = balanceService.getBalance(id);
        return accountMapper.toResponse(account, balance);
    }

    public Account findAccountOrThrow(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
    }
}
