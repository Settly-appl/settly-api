package pl.settly.settly_api.suggestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.suggestions.dto.CreateSuggestionRequest;
import pl.settly.settly_api.suggestions.dto.SuggestionResponse;
import pl.settly.settly_api.suggestions.model.Suggestion;
import pl.settly.settly_api.suggestions.repository.SuggestionRepository;
import pl.settly.settly_api.suggestions.service.SuggestionService;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

  @Mock SuggestionRepository suggestionRepository;
  @Mock UserRepository userRepository;

  @InjectMocks SuggestionService suggestionService;

  private final UUID userId = UUID.randomUUID();

  private User user(String displayName, String username) {
    User u = new User();
    u.setId(userId);
    u.setDisplayName(displayName);
    u.setUsername(username);
    return u;
  }

  private Suggestion suggestion(User author, String content) {
    return Suggestion.builder()
        .id(UUID.randomUUID())
        .user(author)
        .content(content)
        .createdAt(Instant.now())
        .build();
  }

  @Test
  void should_store_a_trimmed_suggestion() {
    User author = user("Mateusz", "mat");
    given(userRepository.getReferenceById(userId)).willReturn(author);
    given(suggestionRepository.save(any(Suggestion.class))).willAnswer(i -> i.getArgument(0));

    SuggestionResponse response =
        suggestionService.create(new CreateSuggestionRequest("  dark mode please  "), userId);

    assertThat(response.content()).isEqualTo("dark mode please");
    assertThat(response.authorName()).isEqualTo("Mateusz");
  }

  @Test
  void should_fall_back_to_the_username_when_there_is_no_display_name() {
    given(suggestionRepository.findAllNewestFirst())
        .willReturn(List.of(suggestion(user(null, "mat"), "add budgets")));

    assertThat(suggestionService.getAll().get(0).authorName()).isEqualTo("mat");
  }

  @Test
  void should_delete_a_suggestion() {
    UUID id = UUID.randomUUID();
    given(suggestionRepository.existsById(id)).willReturn(true);

    suggestionService.delete(id);

    verify(suggestionRepository).deleteById(id);
  }

  @Test
  void should_report_a_missing_suggestion_rather_than_silently_succeed() {
    UUID id = UUID.randomUUID();
    given(suggestionRepository.existsById(id)).willReturn(false);

    assertThatThrownBy(() -> suggestionService.delete(id))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(suggestionRepository, never()).deleteById(id);
  }

  @Test
  void should_keep_a_suggestion_whose_author_was_deleted() {
    // The FK clears on account deletion rather than cascading, so the text survives
    // the person. It still has to render.
    given(suggestionRepository.findAllNewestFirst())
        .willReturn(List.of(suggestion(null, "was worth saying")));

    List<SuggestionResponse> all = suggestionService.getAll();

    assertThat(all).hasSize(1);
    assertThat(all.get(0).authorName()).isNull();
    assertThat(all.get(0).content()).isEqualTo("was worth saying");
  }
}
