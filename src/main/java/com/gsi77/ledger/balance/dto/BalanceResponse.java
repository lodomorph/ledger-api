package com.gsi77.ledger.balance.dto;

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
public class BalanceResponse {

    private UUID accountId;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private OffsetDateTime cachedAt;
}
