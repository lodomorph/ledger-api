package com.gsi77.ledger.account;

import com.gsi77.ledger.account.dto.AccountResponse;
import com.gsi77.ledger.account.dto.CreateAccountRequest;
import com.gsi77.ledger.balance.BalanceService;
import com.gsi77.ledger.balance.dto.BalanceResponse;
import com.gsi77.ledger.transaction.TransactionService;
import com.gsi77.ledger.transaction.dto.PostTransactionRequest;
import com.gsi77.ledger.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account ledger operations")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final BalanceService balanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAccount", summary = "Create a new account")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAccountById", summary = "Get account by UUID")
    public AccountResponse getAccountById(@PathVariable UUID id) {
        return accountService.getAccountById(id);
    }

    @GetMapping("/number/{accountNumber}")
    @Operation(operationId = "getAccountByNumber", summary = "Get account by account number")
    public AccountResponse getAccountByNumber(@PathVariable String accountNumber) {
        return accountService.getAccountByNumber(accountNumber);
    }

    @PatchMapping("/{id}/freeze")
    @Operation(operationId = "freezeAccount", summary = "Freeze an account")
    public AccountResponse freezeAccount(@PathVariable UUID id) {
        return accountService.freezeAccount(id);
    }

    @PatchMapping("/{id}/close")
    @Operation(operationId = "closeAccount", summary = "Close an account")
    public AccountResponse closeAccount(@PathVariable UUID id) {
        return accountService.closeAccount(id);
    }

    @PostMapping("/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "postTransaction", summary = "Post a credit or debit transaction")
    public TransactionResponse postTransaction(@PathVariable UUID id,
                                                @Valid @RequestBody PostTransactionRequest request) {
        return transactionService.postTransaction(id, request);
    }

    @GetMapping("/{id}/transactions")
    @Operation(operationId = "listTransactions", summary = "List all transactions (newest first)")
    public List<TransactionResponse> listTransactions(@PathVariable UUID id) {
        return transactionService.listTransactions(id);
    }

    @GetMapping("/{id}/balance")
    @Operation(operationId = "getBalance", summary = "Get current balance (Redis-cached)")
    public BalanceResponse getBalanceEndpoint(@PathVariable UUID id) {
        return balanceService.getBalanceResponse(id);
    }
}
