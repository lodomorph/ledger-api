package com.gsi77.ledger.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    @NotBlank
    @Size(max = 100)
    private String ownerName;

    @Pattern(regexp = "[A-Z]{3}")
    @Builder.Default
    private String currency = "PHP";
}
