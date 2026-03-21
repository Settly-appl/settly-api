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
import pl.settly.settly_api.auth.user.dto.ProviderUserInfo;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.auth.user.service.UserService;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;

@Service
public class KeycloakAdminService {

    private final Keycloak keycloak;
    private final String realm;
    private final UserService userService;
    private final UserRepository userRepository;

    public KeycloakAdminService(
            @Value("${keycloak.admin.server-url}") String serverUrl,
            @Value("${keycloak.admin.realm}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret,
            UserService userService,
            UserRepository userRepository) {
        this.realm = realm;
        this.keycloak =
                KeycloakBuilder.builder()
                        .serverUrl(serverUrl)
                        .realm(realm)
                        .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .build();
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public Optional<UserRepresentation> findUserById(UUID id) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(id.toString()).toRepresentation();
            return Optional.of(user);
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    public User syncUser(UUID id) {
        return userRepository
                .findById(id)
                .orElseGet(
                        () -> {
                            UserRepresentation kc =
                                    findUserById(id)
                                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                            ProviderUserInfo info =
                                    new ProviderUserInfo(
                                            kc.getId(),
                                            "keycloak",
                                            kc.getEmail(),
                                            kc.getUsername(),
                                            kc.getFirstName() + " " + kc.getLastName(),
                                            null);
                            userService.ensureExists(id, info);
                            return userRepository.getReferenceById(id);
                        });
    }
}
