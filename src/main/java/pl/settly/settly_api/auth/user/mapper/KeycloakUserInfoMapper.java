package pl.settly.settly_api.auth.user.mapper;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import pl.settly.settly_api.auth.user.dto.ProviderUserInfo;

@Component
public class KeycloakUserInfoMapper {

    public ProviderUserInfo map(Jwt jwt) {
        return new ProviderUserInfo(
                jwt.getSubject(),
                "keycloak",
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture")
        );
    }
}
