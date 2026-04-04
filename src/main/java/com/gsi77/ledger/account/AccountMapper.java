package com.gsi77.ledger.account;

import com.gsi77.ledger.account.dto.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "currentBalance", source = "balance")
    AccountResponse toResponse(Account account, BigDecimal balance);
}
