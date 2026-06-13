package pl.settly.settly_api.notifications.dto;

import java.util.UUID;
import pl.settly.settly_api.notifications.model.DeviceToken;
import pl.settly.settly_api.notifications.model.Platform;

public record DeviceTokenResponse(UUID id, String token, Platform platform) {

  public static DeviceTokenResponse from(DeviceToken deviceToken) {
    return new DeviceTokenResponse(
        deviceToken.getId(), deviceToken.getToken(), deviceToken.getPlatform());
  }
}
