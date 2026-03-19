package pl.settly.settly_api.auth.user.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.settly.settly_api.auth.user.dto.ProviderUserInfo;
import pl.settly.settly_api.auth.user.mapper.KeycloakUserInfoMapper;
import pl.settly.settly_api.auth.user.service.UserService;

@Component
public class UserSyncFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final KeycloakUserInfoMapper keycloakUserInfoMapper;

    public UserSyncFilter(UserService userService, KeycloakUserInfoMapper keycloakUserInfoMapper) {
        this.userService = userService;
        this.keycloakUserInfoMapper = keycloakUserInfoMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var jwt = jwtAuth.getToken();
            ProviderUserInfo info = keycloakUserInfoMapper.map(jwt);
            userService.ensureExists(UUID.fromString(jwt.getSubject()), info);
        }

        filterChain.doFilter(request, response);
    }
}
