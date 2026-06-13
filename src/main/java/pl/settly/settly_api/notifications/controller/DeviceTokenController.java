package pl.settly.settly_api.notifications.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.notifications.dto.DeviceTokenResponse;
import pl.settly.settly_api.notifications.dto.RegisterDeviceTokenRequest;
import pl.settly.settly_api.notifications.service.DeviceTokenService;

@RestController
@RequestMapping("/notifications/device-tokens")
public class DeviceTokenController {

  private final DeviceTokenService deviceTokenService;

  public DeviceTokenController(DeviceTokenService deviceTokenService) {
    this.deviceTokenService = deviceTokenService;
  }

  @PostMapping
  public ResponseEntity<DeviceTokenResponse> register(
      @Valid @RequestBody RegisterDeviceTokenRequest request, Authentication authentication) {
    return ResponseEntity.ok(
        deviceTokenService.register(UUID.fromString(authentication.getName()), request));
  }

  @DeleteMapping("/{token}")
  public ResponseEntity<Void> unregister(
      @PathVariable String token, Authentication authentication) {
    deviceTokenService.unregister(UUID.fromString(authentication.getName()), token);
    return ResponseEntity.noContent().build();
  }
}
