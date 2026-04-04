package com.gsi77.ledger.account.dto;

import com.gsi77.ledger.account.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private String ownerName;
    private String currency;
    private AccountStatus status;
    private OffsetDateTime createdAt;
    private BigDecimal currentBalance;
}
