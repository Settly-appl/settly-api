package pl.settly.settly_api.auth.user.dto;

public record ProviderUserInfo(
        String providerId,
        String provider,
        String email,
        String username,
        String displayName,
        String avatarUrl) {}
