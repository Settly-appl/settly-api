package pl.settly.settly_api.auth.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String clientId;

  public KeycloakJwtAuthenticationConverter(@Value("${keycloak.client-id}") String clientId) {
    this.clientId = clientId;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractClientRoles(jwt);
    return new JwtAuthenticationToken(jwt, authorities);
  }

  @SuppressWarnings("unchecked")
  private Collection<GrantedAuthority> extractClientRoles(Jwt jwt) {
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    if (resourceAccess == null) return List.of();

    Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
    if (clientAccess == null) return List.of();

    List<String> roles = (List<String>) clientAccess.get("roles");
    if (roles == null) return List.of();

    return roles.stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .collect(Collectors.toList());
  }
}
