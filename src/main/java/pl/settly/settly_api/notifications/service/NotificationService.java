package pl.settly.settly_api.notifications.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.notifications.dto.NotificationResponse;
import pl.settly.settly_api.notifications.model.UserNotification;
import pl.settly.settly_api.notifications.repository.UserNotificationRepository;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  /** How many unread notifications the bell asks for. */
  private static final int INBOX_LIMIT = 50;

  private final DeviceTokenService deviceTokenService;
  private final FcmService fcmService;
  private final UserNotificationRepository userNotificationRepository;
  private final UserRepository userRepository;

  /** Only used to (de)serialise the small FCM data map; no Jackson bean is configured here. */
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public NotificationService(
      DeviceTokenService deviceTokenService,
      FcmService fcmService,
      UserNotificationRepository userNotificationRepository,
      UserRepository userRepository) {
    this.deviceTokenService = deviceTokenService;
    this.fcmService = fcmService;
    this.userNotificationRepository = userNotificationRepository;
    this.userRepository = userRepository;
  }

  /**
   * Raises a notification for a user: records it in their inbox, then pushes it.
   *
   * <p>The inbox is written <em>first and unconditionally</em>, because the push is
   * fire-and-forget. If the user dismisses the toast, never taps it, has no device token, or their
   * browser refuses to register for push, the notification would otherwise vanish without trace.
   * Persisting it means the bell can still show it — the inbox, not the push, is the source of
   * truth.
   */
  @Transactional
  public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
    UserNotification saved =
        userNotificationRepository.save(
            UserNotification.builder()
                .user(userRepository.getReferenceById(userId))
                .title(title)
                .body(body)
                .type(data == null ? null : data.get("type"))
                .data(writeData(data))
                .read(false)
                .build());

    List<String> tokens = deviceTokenService.tokensForUser(userId);
    if (tokens.isEmpty()) {
      return; // nothing to push to — it still sits in their inbox
    }

    // Carry the inbox id, so tapping the push can mark that exact entry read and
    // it stops nagging from the bell.
    Map<String, String> payload = new HashMap<>(data == null ? Map.of() : data);
    payload.put("notificationId", saved.getId().toString());

    List<String> invalid = fcmService.send(tokens, title, body, payload);
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

  /** The bell: unread notifications, newest first. Read ones are done with and stay hidden. */
  @Transactional(readOnly = true)
  public List<NotificationResponse> inbox(UUID userId) {
    return userNotificationRepository
        .findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, Limit.of(INBOX_LIMIT))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  /** Marks one entry read — called when the user taps the push toast or the bell entry. */
  @Transactional
  public void markRead(UUID notificationId, UUID userId) {
    UserNotification notification =
        userNotificationRepository
            .findById(notificationId)
            .filter(n -> n.getUser().getId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Notification does not exist"));

    if (notification.isRead()) {
      return; // idempotent: a tap may well arrive twice
    }
    notification.setRead(true);
    notification.setReadAt(Instant.now());
    userNotificationRepository.save(notification);
  }

  @Transactional
  public void markAllRead(UUID userId) {
    List<UserNotification> unread = userNotificationRepository.findByUserIdAndReadFalse(userId);
    Instant now = Instant.now();
    for (UserNotification n : unread) {
      n.setRead(true);
      n.setReadAt(now);
    }
    userNotificationRepository.saveAll(unread);
  }

  private NotificationResponse toResponse(UserNotification n) {
    return new NotificationResponse(
        n.getId(), n.getTitle(), n.getBody(), n.getType(), readData(n.getData()), n.getCreatedAt());
  }

  private String writeData(Map<String, String> data) {
    if (data == null || data.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(data);
    } catch (Exception e) {
      log.warn("Could not serialise notification data", e);
      return null;
    }
  }

  private Map<String, String> readData(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      log.warn("Could not read notification data", e);
      return Map.of();
    }
  }
}
