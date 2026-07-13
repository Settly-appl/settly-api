package pl.settly.settly_api.notifications.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.notifications.model.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

  /** The bell: a user's unread notifications, newest first. */
  List<UserNotification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Limit limit);

  List<UserNotification> findByUserIdAndReadFalse(UUID userId);
}
