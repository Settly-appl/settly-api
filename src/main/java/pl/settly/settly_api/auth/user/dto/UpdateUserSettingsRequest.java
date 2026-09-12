package pl.settly.settly_api.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserSettingsRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String baseCurrency) {}
