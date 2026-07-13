package pl.settly.settly_api.notifications.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.notifications.dto.BroadcastNotificationRequest;
import pl.settly.settly_api.notifications.dto.NotificationResponse;
import pl.settly.settly_api.notifications.service.NotificationService;
import pl.settly.settly_api.notifications.service.SettlementReminderJob;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService notificationService;
  private final SettlementReminderJob settlementReminderJob;

  public NotificationController(
      NotificationService notificationService, SettlementReminderJob settlementReminderJob) {
    this.notificationService = notificationService;
    this.settlementReminderJob = settlementReminderJob;
  }

  /** Admin-only: push a notification to every registered device. */
  @PostMapping("/broadcast")
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastNotificationRequest request) {
    notificationService.broadcast(request.title(), request.body());
    return ResponseEntity.accepted().build();
  }

  /** The bell: this user's unread notifications, newest first. */
  @GetMapping
  public ResponseEntity<List<NotificationResponse>> inbox(Authentication authentication) {
    return ResponseEntity.ok(notificationService.inbox(UUID.fromString(authentication.getName())));
  }

  /**
   * Marks one notification read, so it stops showing in the bell. Called both when the user taps
   * the push toast (the payload carries the inbox id) and when they tap the entry in the bell.
   */
  @PatchMapping("/{notificationId}/read")
  public ResponseEntity<Void> markRead(
      @PathVariable UUID notificationId, Authentication authentication) {
    notificationService.markRead(notificationId, UUID.fromString(authentication.getName()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead(Authentication authentication) {
    notificationService.markAllRead(UUID.fromString(authentication.getName()));
    return ResponseEntity.noContent().build();
  }

  /**
   * Admin-only: run the daily settle-up reminder now instead of waiting for 18:00. Reports how many
   * people were nudged, so an empty result ("nobody owes anything") is distinguishable from a
   * silent failure.
   */
  @PostMapping("/settlement-reminder")
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<Map<String, Integer>> triggerSettlementReminder() {
    int reminded = settlementReminderJob.sendReminders();
    return ResponseEntity.ok(Map.of("reminded", reminded));
  }
}
