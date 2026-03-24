package pl.settly.settly_api.auth.keycloak;

import jakarta.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.model.UserIdentityProvider;
import pl.settly.settly_api.auth.user.repository.UserIdentityProviderRepository;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;

@Service
public class KeycloakAdminService {

  private final Keycloak keycloak;
  private final String realm;
  private final UserRepository userRepository;
  private final UserIdentityProviderRepository identityProviderRepository;

  public KeycloakAdminService(
      @Value("${keycloak.admin.server-url}") String serverUrl,
      @Value("${keycloak.admin.realm}") String realm,
      @Value("${keycloak.admin.client-id}") String clientId,
      @Value("${keycloak.admin.client-secret}") String clientSecret,
      UserRepository userRepository,
      UserIdentityProviderRepository identityProviderRepository) {
    this.realm = realm;
    this.keycloak =
        KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build();
    this.userRepository = userRepository;
    this.identityProviderRepository = identityProviderRepository;
  }

  public Optional<UserRepresentation> findUserById(UUID id) {
    try {
      UserRepresentation user = keycloak.realm(realm).users().get(id.toString()).toRepresentation();
      return Optional.of(user);
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }

  @Transactional
  public User syncUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseGet(
            () -> {
              UserRepresentation kc =
                  findUserById(id)
                      .orElseThrow(() -> new ResourceNotFoundException("User not found"));

              User user = new User();
              user.setId(id);
              user.setEmail(kc.getEmail());
              user.setUsername(kc.getUsername());
              user.setDisplayName(kc.getFirstName() + " " + kc.getLastName());
              user.setAvatarUrl(null);
              userRepository.save(user);

              UserIdentityProvider idp = new UserIdentityProvider();
              idp.setId(UUID.randomUUID());
              idp.setUser(user);
              idp.setEmail(kc.getEmail());
              idp.setProvider("keycloak");
              idp.setProviderId(kc.getId());
              identityProviderRepository.save(idp);

              return user;
            });
  }
}
