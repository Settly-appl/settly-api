package pl.settly.settly_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.notifications.model.UserNotification;
import pl.settly.settly_api.notifications.repository.UserNotificationRepository;
import pl.settly.settly_api.notifications.service.DeviceTokenService;
import pl.settly.settly_api.notifications.service.FcmService;
import pl.settly.settly_api.notifications.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock DeviceTokenService deviceTokenService;
  @Mock FcmService fcmService;
  @Mock UserNotificationRepository userNotificationRepository;
  @Mock UserRepository userRepository;

  @InjectMocks NotificationService notificationService;

  private final UUID userId = UUID.randomUUID();

  private void stubSave() {
    given(userNotificationRepository.save(any(UserNotification.class)))
        .willAnswer(
            inv -> {
              UserNotification n = inv.getArgument(0);
              n.setId(UUID.randomUUID());
              return n;
            });
    given(userRepository.getReferenceById(userId)).willReturn(new User());
  }

  @Test
  void should_record_the_notification_even_when_the_user_has_no_device_token() {
    // The whole point of the inbox: a user whose browser refuses push (or who never
    // granted permission) must still see the notification in the bell.
    stubSave();
    given(deviceTokenService.tokensForUser(userId)).willReturn(List.of());

    notificationService.sendToUser(userId, "Tytuł", "Treść", Map.of("type", "EXPENSE_SPLIT"));

    ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
    verify(userNotificationRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle()).isEqualTo("Tytuł");
    assertThat(captor.getValue().isRead()).isFalse();

    // No token — nothing to push to, but the inbox entry stands.
    verify(fcmService, never()).send(anyList(), anyString(), anyString(), any());
  }

  @Test
  void should_push_with_the_inbox_id_so_a_tap_can_mark_it_read() {
    stubSave();
    given(deviceTokenService.tokensForUser(userId)).willReturn(List.of("token-1"));
    given(fcmService.send(anyList(), anyString(), anyString(), any())).willReturn(List.of());

    notificationService.sendToUser(userId, "Tytuł", "Treść", Map.of("type", "EXPENSE_SPLIT"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
    verify(fcmService).send(anyList(), anyString(), anyString(), data.capture());

    assertThat(data.getValue()).containsKey("notificationId");
    assertThat(data.getValue()).containsEntry("type", "EXPENSE_SPLIT");
  }

  @Test
  void should_mark_read_and_be_idempotent() {
    UUID notificationId = UUID.randomUUID();
    User owner = new User();
    owner.setId(userId);
    UserNotification n =
        UserNotification.builder().id(notificationId).user(owner).read(false).build();

    given(userNotificationRepository.findById(notificationId)).willReturn(java.util.Optional.of(n));

    notificationService.markRead(notificationId, userId);
    assertThat(n.isRead()).isTrue();
    assertThat(n.getReadAt()).isNotNull();

    // A second tap (the toast and the bell entry both fire) must not blow up.
    notificationService.markRead(notificationId, userId);
    assertThat(n.isRead()).isTrue();
  }

  @Test
  void should_not_let_someone_mark_another_users_notification_read() {
    UUID notificationId = UUID.randomUUID();
    User owner = new User();
    owner.setId(userId);
    UserNotification n = UserNotification.builder().id(notificationId).user(owner).build();

    given(userNotificationRepository.findById(notificationId)).willReturn(java.util.Optional.of(n));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> notificationService.markRead(notificationId, UUID.randomUUID()))
        .isInstanceOf(pl.settly.settly_api.common.exception.ResourceNotFoundException.class);
  }
}
