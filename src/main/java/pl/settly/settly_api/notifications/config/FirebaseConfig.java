package pl.settly.settly_api.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

  @Bean
  public FirebaseApp firebaseApp(@Value("${firebase.credentials}") String base64Credentials)
      throws Exception {
    if (base64Credentials == null || base64Credentials.isBlank()) {
      throw new IllegalStateException(
          "firebase.credentials (FIREBASE_CREDENTIALS) must be set when the prod profile is active");
    }

    byte[] json = Base64.getDecoder().decode(base64Credentials.getBytes(StandardCharsets.UTF_8));
    GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(json));

    FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();

    if (FirebaseApp.getApps().isEmpty()) {
      return FirebaseApp.initializeApp(options);
    }
    return FirebaseApp.getInstance();
  }

  @Bean
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }
}
