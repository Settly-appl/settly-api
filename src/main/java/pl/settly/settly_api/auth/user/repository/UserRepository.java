package pl.settly.settly_api.auth.user.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.auth.user.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);
}
