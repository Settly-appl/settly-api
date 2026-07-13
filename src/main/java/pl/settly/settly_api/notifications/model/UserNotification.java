package pl.settly.settly_api.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import pl.settly.settly_api.auth.user.model.User;

/**
 * A notification raised for a user, kept regardless of whether the push was delivered.
 *
 * <p>Named UserNotification rather than Notification to stay clearly distinct from
 * com.google.firebase.messaging.Notification, which is the payload we hand to FCM.
 */
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {
  @Id @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String body;

  @Column private String type;

  /** The FCM data payload as JSON, so the bell deep-links exactly like the push would. */
  @Column private String data;

  /** Read once the user acts on it — by tapping the push toast or the bell entry. */
  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;
}
