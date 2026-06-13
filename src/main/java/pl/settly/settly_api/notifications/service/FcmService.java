package pl.settly.settly_api.notifications.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FcmService {

  private static final Logger log = LoggerFactory.getLogger(FcmService.class);

  private final ObjectProvider<FirebaseMessaging> firebaseMessaging;

  public FcmService(ObjectProvider<FirebaseMessaging> firebaseMessaging) {
    this.firebaseMessaging = firebaseMessaging;
  }

  private static final int MAX_BATCH = 500;

  public List<String> send(
      List<String> tokens, String title, String body, Map<String, String> data) {
    FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
    if (messaging == null) {
      log.debug("FCM not configured; skipping push to {} token(s)", tokens.size());
      return List.of();
    }
    if (tokens.isEmpty()) {
      return List.of();
    }

    List<String> invalid = new ArrayList<>();
    for (int start = 0; start < tokens.size(); start += MAX_BATCH) {
      List<String> batch = tokens.subList(start, Math.min(start + MAX_BATCH, tokens.size()));
      invalid.addAll(sendBatch(messaging, batch, title, body, data));
    }
    return invalid;
  }

  private List<String> sendBatch(
      FirebaseMessaging messaging,
      List<String> tokens,
      String title,
      String body,
      Map<String, String> data) {
    MulticastMessage message =
        MulticastMessage.builder()
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data == null ? Map.of() : data)
            .addAllTokens(tokens)
            .build();

    try {
      BatchResponse response = messaging.sendEachForMulticast(message);
      List<String> invalid = new ArrayList<>();
      List<SendResponse> responses = response.getResponses();
      for (int i = 0; i < responses.size(); i++) {
        SendResponse r = responses.get(i);
        if (r.isSuccessful()) {
          continue;
        }
        MessagingErrorCode code =
            r.getException() == null ? null : r.getException().getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED
            || code == MessagingErrorCode.INVALID_ARGUMENT) {
          invalid.add(tokens.get(i));
        } else {
          log.warn(
              "FCM send failed for a token: {}",
              r.getException() == null ? "unknown error" : r.getException().getMessage());
        }
      }
      return invalid;
    } catch (FirebaseMessagingException e) {
      log.error("FCM multicast send failed", e);
      return List.of();
    }
  }
}
