package com.gsi77.ledger.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountNumberGenerator generator;

    @Test
    void generate_shouldReturnCorrectFormat() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        String number = generator.generate();

        assertThat(number).matches("ACC\\d{7}");
    }

    @Test
    void generate_shouldRetryOnCollision() {
        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        String number = generator.generate();

        assertThat(number).matches("ACC\\d{7}");
    }

    @Test
    void generate_shouldThrowAfterMaxAttempts() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate unique account number");
    }
}
