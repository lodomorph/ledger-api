package com.gsi77.ledger.balance;

import com.gsi77.ledger.account.Account;
import com.gsi77.ledger.account.AccountRepository;
import com.gsi77.ledger.balance.dto.BalanceResponse;
import com.gsi77.ledger.exception.AccountNotFoundException;
import com.gsi77.ledger.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceService {

    private static final String BALANCE_KEY_PREFIX = "balance:";
    private static final Duration BALANCE_TTL = Duration.ofSeconds(60);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    public BigDecimal getBalance(UUID accountId) {
        String key = BALANCE_KEY_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            log.debug("Cache hit for balance:{}", accountId);
            return new BigDecimal(cached);
        }

        log.debug("Cache miss for balance:{}", accountId);
        BigDecimal balance = transactionRepository.computeBalance(accountId);
        redisTemplate.opsForValue().set(key, balance.toPlainString(), BALANCE_TTL);
        return balance;
    }

    public void evictBalance(UUID accountId) {
        String key = BALANCE_KEY_PREFIX + accountId;
        redisTemplate.delete(key);
        log.debug("Evicted cache for balance:{}", accountId);
    }

    public BalanceResponse getBalanceResponse(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));

        BigDecimal balance = getBalance(accountId);

        return BalanceResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .balance(balance)
                .currency(account.getCurrency())
                .cachedAt(OffsetDateTime.now())
                .build();
    }
}
