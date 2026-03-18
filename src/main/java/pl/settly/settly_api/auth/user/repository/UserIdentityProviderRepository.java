package pl.settly.settly_api.auth.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.settly.settly_api.auth.user.model.UserIdentityProvider;

public interface UserIdentityProviderRepository extends JpaRepository<UserIdentityProvider, UUID> {
}
