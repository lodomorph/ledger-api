package com.gsi77.ledger.account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    public String generate() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String number = "ACC" + String.format("%07d", ThreadLocalRandom.current().nextInt(0, 10_000_000));
            if (!accountRepository.existsByAccountNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Unable to generate unique account number after 10 attempts");
    }
}
