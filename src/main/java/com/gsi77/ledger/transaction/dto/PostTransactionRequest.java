package com.gsi77.ledger.transaction.dto;

import com.gsi77.ledger.transaction.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostTransactionRequest {

    @NotNull
    private TransactionType type;

    @NotNull
    @Positive
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    private String description;
}
