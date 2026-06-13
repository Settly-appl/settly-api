package pl.settly.settly_api.notifications.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private final DeviceTokenService deviceTokenService;
  private final FcmService fcmService;

  public NotificationService(DeviceTokenService deviceTokenService, FcmService fcmService) {
    this.deviceTokenService = deviceTokenService;
    this.fcmService = fcmService;
  }

  public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
    List<String> tokens = deviceTokenService.tokensForUser(userId);
    if (tokens.isEmpty()) {
      return;
    }
    List<String> invalid = fcmService.send(tokens, title, body, data);
    deviceTokenService.pruneTokens(invalid);
  }

  public void broadcast(String title, String body) {
    List<String> tokens = deviceTokenService.allTokens();
    if (tokens.isEmpty()) {
      return;
    }
    List<String> invalid = fcmService.send(tokens, title, body, Map.of("type", "BROADCAST"));
    deviceTokenService.pruneTokens(invalid);
  }
}
