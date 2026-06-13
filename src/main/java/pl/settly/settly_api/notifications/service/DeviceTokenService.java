package pl.settly.settly_api.notifications.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.notifications.dto.DeviceTokenResponse;
import pl.settly.settly_api.notifications.dto.RegisterDeviceTokenRequest;
import pl.settly.settly_api.notifications.model.DeviceToken;
import pl.settly.settly_api.notifications.repository.DeviceTokenRepository;

@Service
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final UserRepository userRepository;

  public DeviceTokenService(
      DeviceTokenRepository deviceTokenRepository, UserRepository userRepository) {
    this.deviceTokenRepository = deviceTokenRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public DeviceTokenResponse register(UUID userId, RegisterDeviceTokenRequest request) {
    DeviceToken deviceToken =
        deviceTokenRepository
            .findByToken(request.token())
            .map(
                existing -> {
                  existing.setUser(userRepository.getReferenceById(userId));
                  existing.setPlatform(request.platform());
                  return existing;
                })
            .orElseGet(
                () ->
                    DeviceToken.builder()
                        .user(userRepository.getReferenceById(userId))
                        .token(request.token())
                        .platform(request.platform())
                        .build());

    return DeviceTokenResponse.from(deviceTokenRepository.save(deviceToken));
  }

  @Transactional
  public void unregister(UUID userId, String token) {
    deviceTokenRepository
        .findByToken(token)
        .filter(t -> t.getUser().getId().equals(userId))
        .ifPresent(deviceTokenRepository::delete);
  }

  @Transactional(readOnly = true)
  public List<String> tokensForUser(UUID userId) {
    return deviceTokenRepository.findByUserId(userId).stream().map(DeviceToken::getToken).toList();
  }

  @Transactional(readOnly = true)
  public List<String> allTokens() {
    return deviceTokenRepository.findAllTokens();
  }

  @Transactional
  public void pruneTokens(Collection<String> tokens) {
    if (!tokens.isEmpty()) {
      deviceTokenRepository.deleteByTokenIn(tokens);
    }
  }
}
