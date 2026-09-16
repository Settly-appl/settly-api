package pl.settly.settly_api.suggestions.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.suggestions.dto.CreateSuggestionRequest;
import pl.settly.settly_api.suggestions.dto.SuggestionResponse;
import pl.settly.settly_api.suggestions.model.Suggestion;
import pl.settly.settly_api.suggestions.repository.SuggestionRepository;

@Service
public class SuggestionService {

  private final SuggestionRepository suggestionRepository;
  private final UserRepository userRepository;

  public SuggestionService(
      SuggestionRepository suggestionRepository, UserRepository userRepository) {
    this.suggestionRepository = suggestionRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public SuggestionResponse create(CreateSuggestionRequest request, UUID userId) {
    Suggestion saved =
        suggestionRepository.save(
            Suggestion.builder()
                .user(userRepository.getReferenceById(userId))
                .content(request.content().trim())
                .build());
    return toResponse(saved);
  }

  /** Admin-only listing; the endpoint enforces that, not this method. */
  @Transactional(readOnly = true)
  public List<SuggestionResponse> getAll() {
    return suggestionRepository.findAllNewestFirst().stream().map(this::toResponse).toList();
  }

  /**
   * Mapped by hand rather than with MapStruct: the author is one readable name assembled from
   * whichever of displayName/username exists, which is a rule rather than a field copy.
   */
  private SuggestionResponse toResponse(Suggestion suggestion) {
    return new SuggestionResponse(
        suggestion.getId(),
        suggestion.getContent(),
        authorName(suggestion.getUser()),
        suggestion.getCreatedAt());
  }

  private String authorName(User user) {
    if (user == null) {
      return null; // the account was deleted; the suggestion still stands
    }
    String displayName = user.getDisplayName();
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    return user.getUsername();
  }
}
