package pl.settly.settly_api.notifications.listener;

import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.notifications.event.ExpenseSplitCreatedEvent;
import pl.settly.settly_api.notifications.event.FriendRequestAcceptedEvent;
import pl.settly.settly_api.notifications.event.FriendRequestedEvent;
import pl.settly.settly_api.notifications.service.NotificationService;

@Component
public class NotificationEventListener {

  private final NotificationService notificationService;
  private final UserRepository userRepository;

  public NotificationEventListener(
      NotificationService notificationService, UserRepository userRepository) {
    this.notificationService = notificationService;
    this.userRepository = userRepository;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFriendRequested(FriendRequestedEvent event) {
    String actor = displayName(event.actorId());
    notificationService.sendToUser(
        event.recipientId(),
        "New friend request",
        actor + " sent you a friend request",
        Map.of("type", "FRIEND_REQUEST", "actorId", event.actorId().toString()));
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFriendRequestAccepted(FriendRequestAcceptedEvent event) {
    String actor = displayName(event.actorId());
    notificationService.sendToUser(
        event.recipientId(),
        "Friend request accepted",
        actor + " accepted your friend request",
        Map.of("type", "FRIEND_REQUEST_ACCEPTED", "actorId", event.actorId().toString()));
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onExpenseSplitCreated(ExpenseSplitCreatedEvent event) {
    String actor = displayName(event.actorId());
    String where = event.shop() == null || event.shop().isBlank() ? "an expense" : event.shop();
    String body = actor + " added you to a split for " + where;
    Map<String, String> data =
        Map.of(
            "type",
            "EXPENSE_SPLIT",
            "actorId",
            event.actorId().toString(),
            "expenseId",
            event.expenseId().toString());
    for (UUID recipientId : event.recipientIds()) {
      notificationService.sendToUser(recipientId, "New shared expense", body, data);
    }
  }

  private String displayName(UUID userId) {
    return userRepository
        .findById(userId)
        .map(User::getDisplayName)
        .filter(name -> name != null && !name.isBlank())
        .orElse("Someone");
  }
}
