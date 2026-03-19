package pl.settly.settly_api.auth.user.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.dto.ProviderUserInfo;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.model.UserIdentityProvider;
import pl.settly.settly_api.auth.user.repository.UserIdentityProviderRepository;
import pl.settly.settly_api.auth.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;

    public UserService(
            UserRepository userRepository, UserIdentityProviderRepository identityProviderRepository) {
        this.userRepository = userRepository;
        this.identityProviderRepository = identityProviderRepository;
    }

    @Cacheable(value = "knownUsers", key = "#sub.toString()")
    @Transactional
    public boolean ensureExists(UUID sub, ProviderUserInfo info) {
        if (!userRepository.existsById(sub)) {
            User user = new User();
            user.setId(sub);
            user.setEmail(info.email());
            user.setUsername(info.username());
            user.setDisplayName(info.displayName());
            user.setAvatarUrl(info.avatarUrl());
            userRepository.save(user);

            UserIdentityProvider idp = new UserIdentityProvider();
            idp.setId(UUID.randomUUID());
            idp.setUser(user);
            idp.setEmail(info.email());
            idp.setProvider(info.provider());
            idp.setProviderId(info.providerId());
            identityProviderRepository.save(idp);
        }
        return true;
    }
}
