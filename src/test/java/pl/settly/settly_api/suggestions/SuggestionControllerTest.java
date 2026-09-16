package pl.settly.settly_api.suggestions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.settly.settly_api.auth.config.KeycloakJwtAuthenticationConverter;
import pl.settly.settly_api.auth.config.SecurityConfig;
import pl.settly.settly_api.auth.user.filter.UserSyncFilter;
import pl.settly.settly_api.auth.user.mapper.KeycloakUserInfoMapper;
import pl.settly.settly_api.auth.user.service.UserService;
import pl.settly.settly_api.suggestions.controller.SuggestionController;
import pl.settly.settly_api.suggestions.dto.CreateSuggestionRequest;
import pl.settly.settly_api.suggestions.dto.SuggestionResponse;
import pl.settly.settly_api.suggestions.service.SuggestionService;

@WebMvcTest(SuggestionController.class)
@Import(SecurityConfig.class)
class SuggestionControllerTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

  @Autowired MockMvc mockMvc;

  @MockitoBean SuggestionService suggestionService;
  @MockitoBean KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
  @MockitoBean UserSyncFilter userSyncFilter;
  @MockitoBean UserService userService;
  @MockitoBean KeycloakUserInfoMapper keycloakUserInfoMapper;

  @Test
  void should_accept_a_suggestion_from_any_signed_in_user() throws Exception {
    given(
            suggestionService.create(
                any(CreateSuggestionRequest.class), eq(UUID.fromString(USER_ID))))
        .willReturn(
            new SuggestionResponse(UUID.randomUUID(), "dark mode", "Mateusz", Instant.now()));

    mockMvc
        .perform(
            post("/suggestions")
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"dark mode\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("dark mode"));
  }

  @Test
  void should_reject_an_empty_suggestion() throws Exception {
    mockMvc
        .perform(
            post("/suggestions")
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"   \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void should_let_an_admin_read_every_suggestion() throws Exception {
    given(suggestionService.getAll())
        .willReturn(
            List.of(new SuggestionResponse(UUID.randomUUID(), "add budgets", "Ala", Instant.now())));

    mockMvc
        .perform(get("/suggestions").with(user(USER_ID).roles("admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("add budgets"));
  }

  @Test
  void should_refuse_a_non_admin_reading_suggestions() throws Exception {
    // Hiding the button in the app is a courtesy; this is the actual control.
    mockMvc
        .perform(get("/suggestions").with(user(USER_ID)))
        .andExpect(status().isForbidden());
  }
}
