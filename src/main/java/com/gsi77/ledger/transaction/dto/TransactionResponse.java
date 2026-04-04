package com.gsi77.ledger.transaction.dto;

import com.gsi77.ledger.transaction.TransactionType;
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
public class TransactionResponse {

    private UUID id;
    private UUID accountId;
    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private BigDecimal balanceAfter;
    private OffsetDateTime createdAt;
}
