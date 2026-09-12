package pl.settly.settly_api.auth.user.dto;

import java.util.List;

/**
 * The requesting user's own settings. {@code supportedCurrencies} is served rather than hardcoded
 * in the client so the picker and the validation can never drift apart.
 */
public record UserSettingsResponse(String baseCurrency, List<String> supportedCurrencies) {}
