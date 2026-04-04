package com.gsi77.ledger.transaction;

import com.gsi77.ledger.account.Account;
import com.gsi77.ledger.account.AccountService;
import com.gsi77.ledger.account.AccountStatus;
import com.gsi77.ledger.balance.BalanceService;
import com.gsi77.ledger.exception.AccountNotActiveException;
import com.gsi77.ledger.exception.InsufficientFundsException;
import com.gsi77.ledger.transaction.dto.PostTransactionRequest;
import com.gsi77.ledger.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountService accountService;
    private final BalanceService balanceService;

    @Transactional
    public TransactionResponse postTransaction(UUID accountId, PostTransactionRequest request) {
        Account account = accountService.findAccountOrThrow(accountId);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Cannot post transaction on " + account.getStatus() + " account");
        }

        BigDecimal currentBalance = transactionRepository.computeBalance(accountId);

        if (request.getType() == TransactionType.DEBIT && request.getAmount().compareTo(currentBalance) > 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: requested " + request.getAmount() + ", available " + currentBalance);
        }

        BigDecimal balanceAfter = request.getType() == TransactionType.CREDIT
                ? currentBalance.add(request.getAmount())
                : currentBalance.subtract(request.getAmount());

        Transaction transaction = Transaction.builder()
                .account(account)
                .type(request.getType())
                .amount(request.getAmount())
                .description(request.getDescription())
                .balanceAfter(balanceAfter)
                .build();

        transaction = transactionRepository.save(transaction);

        balanceService.evictBalance(accountId);

        return transactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(UUID accountId) {
        accountService.findAccountOrThrow(accountId);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
    }
}
