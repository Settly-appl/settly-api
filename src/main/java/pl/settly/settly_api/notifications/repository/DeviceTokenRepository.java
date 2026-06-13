package pl.settly.settly_api.notifications.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.settly.settly_api.notifications.model.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

  List<DeviceToken> findByUserId(UUID userId);

  Optional<DeviceToken> findByToken(String token);

  void deleteByToken(String token);

  void deleteByTokenIn(Collection<String> tokens);

  @Query("select dt.token from DeviceToken dt")
  List<String> findAllTokens();
}
