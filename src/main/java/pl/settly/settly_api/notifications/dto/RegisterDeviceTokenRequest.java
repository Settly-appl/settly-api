package pl.settly.settly_api.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.settly.settly_api.notifications.model.Platform;

public record RegisterDeviceTokenRequest(
    @NotBlank @Size(max = 500) String token, @NotNull Platform platform) {}
