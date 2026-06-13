package pl.settly.settly_api.notifications.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.notifications.dto.BroadcastNotificationRequest;
import pl.settly.settly_api.notifications.service.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  /** Admin-only: push a notification to every registered device. */
  @PostMapping("/broadcast")
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastNotificationRequest request) {
    notificationService.broadcast(request.title(), request.body());
    return ResponseEntity.accepted().build();
  }
}
