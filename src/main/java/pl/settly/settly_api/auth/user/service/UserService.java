package pl.settly.settly_api.auth.user.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.dto.ProviderUserInfo;
import pl.settly.settly_api.auth.user.dto.UserMapper;
import pl.settly.settly_api.auth.user.dto.UserSearchResponse;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.model.UserIdentityProvider;
import pl.settly.settly_api.auth.user.repository.UserIdentityProviderRepository;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.auth.user.dto.UpdateUserSettingsRequest;
import pl.settly.settly_api.auth.user.dto.UserSettingsResponse;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.common.money.CurrencyConversionService;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final UserIdentityProviderRepository identityProviderRepository;
  private final UserMapper userMapper;
  private final CurrencyConversionService currencyConversionService;

  public UserService(
      UserRepository userRepository,
      UserIdentityProviderRepository identityProviderRepository,
      UserMapper userMapper,
      CurrencyConversionService currencyConversionService) {
    this.userRepository = userRepository;
    this.identityProviderRepository = identityProviderRepository;
    this.userMapper = userMapper;
    this.currencyConversionService = currencyConversionService;
  }

  public UserSettingsResponse getSettings(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    return new UserSettingsResponse(
        user.getBaseCurrency(), CurrencyConversionService.SUPPORTED.stream().sorted().toList());
  }

  /**
   * Changes the currency this user's balances and totals are reported in.
   *
   * <p>Past expenses keep the base currency and rate they were created with, so the change is not
   * retroactive: what a trip cost is a fact about the day it happened, and restating it from a rate
   * typed months later would be a guess presented as history.
   */
  @Transactional
  public UserSettingsResponse updateSettings(UUID userId, UpdateUserSettingsRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    user.setBaseCurrency(currencyConversionService.normalize(request.baseCurrency(), null));
    userRepository.save(user);
    return new UserSettingsResponse(
        user.getBaseCurrency(), CurrencyConversionService.SUPPORTED.stream().sorted().toList());
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

  public UserSearchResponse searchByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .map(userMapper::toUserSearchResponse)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  public UserSearchResponse getUserById(UUID userId) {
    return userRepository
        .findById(userId)
        .map(userMapper::toUserSearchResponse)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }
}
