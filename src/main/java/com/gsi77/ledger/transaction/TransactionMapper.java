package com.gsi77.ledger.transaction;

import com.gsi77.ledger.transaction.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "accountId", source = "account.id")
    TransactionResponse toResponse(Transaction transaction);
}
