package pl.settly.settly_api.auth.user.dto;

public record UserSearchResponse(
    String id, String username, String displayName, String avatarUrl) {}
