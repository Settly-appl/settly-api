package pl.settly.settly_api.suggestions.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.suggestions.dto.CreateSuggestionRequest;
import pl.settly.settly_api.suggestions.dto.SuggestionResponse;
import pl.settly.settly_api.suggestions.service.SuggestionService;

@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

  private final SuggestionService suggestionService;

  public SuggestionController(SuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  /** Anyone signed in can send one. */
  @PostMapping
  public ResponseEntity<SuggestionResponse> create(
      @Valid @RequestBody CreateSuggestionRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(suggestionService.create(request, UUID.fromString(authentication.getName())));
  }

  /**
   * Admin-only: read everyone's suggestions. Guarded here rather than in the UI — hiding the button
   * in the app is a courtesy, not a control, and the endpoint is what actually decides.
   */
  @GetMapping
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<List<SuggestionResponse>> getAll() {
    return ResponseEntity.ok(suggestionService.getAll());
  }

  /**
   * Admin-only: throw one away once it has been read and acted on (or not). Same gate as reading —
   * anyone who cannot see a suggestion has no business deleting it either.
   */
  @DeleteMapping("/{suggestionId}")
  @PreAuthorize("hasRole('admin')")
  public ResponseEntity<Void> delete(@PathVariable UUID suggestionId) {
    suggestionService.delete(suggestionId);
    return ResponseEntity.noContent().build();
  }
}
