package pl.settly.settly_api.notifications.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.notifications.dto.BroadcastNotificationRequest;
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
