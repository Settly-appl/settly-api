package pl.settly.settly_api.friendships;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import pl.settly.settly_api.friendships.controller.FriendshipController;
import pl.settly.settly_api.friendships.dto.FriendResponse;
import pl.settly.settly_api.friendships.dto.FriendshipUserDto;
import pl.settly.settly_api.friendships.dto.PendingFriendshipRequestsResponse;
import pl.settly.settly_api.friendships.dto.RequestFriendshipResponse;
import pl.settly.settly_api.friendships.model.FriendshipStatus;
import pl.settly.settly_api.friendships.service.FriendshipService;

@WebMvcTest(FriendshipController.class)
@Import(SecurityConfig.class)
class FriendshipControllerTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String RECEIVER_ID = "22222222-2222-2222-2222-222222222222";

  @Autowired MockMvc mockMvc;

  @MockitoBean FriendshipService friendshipService;
  @MockitoBean KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;
  @MockitoBean UserSyncFilter userSyncFilter;
  @MockitoBean UserService userService;
  @MockitoBean KeycloakUserInfoMapper keycloakUserInfoMapper;

  @BeforeEach
  void setUp() throws Exception {
    doAnswer(
            inv -> {
              inv.getArgument(2, FilterChain.class)
                  .doFilter(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(userSyncFilter)
        .doFilter(any(), any(), any());
  }

  // region requestFriendship

  @Test
  void should_return_200_when_friendship_requested() throws Exception {
    RequestFriendshipResponse response =
        new RequestFriendshipResponse(FriendshipStatus.PENDING, Instant.now());

    given(friendshipService.requestFriendship(any(), eq(UUID.fromString(USER_ID))))
        .willReturn(response);

    mockMvc
        .perform(
            post("/friendships/request")
                .with(user(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + RECEIVER_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void should_return_401_when_requesting_friendship_without_auth() throws Exception {
    mockMvc
        .perform(
            post("/friendships/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + RECEIVER_ID + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region respondToFriendship

  @Test
  void should_return_200_when_responding_to_friendship() throws Exception {
    UUID friendshipId = UUID.randomUUID();
    RequestFriendshipResponse response =
        new RequestFriendshipResponse(FriendshipStatus.ACCEPTED, Instant.now());

    given(
            friendshipService.respondToFriendship(
                eq(friendshipId), eq("ACCEPTED"), eq(UUID.fromString(USER_ID))))
        .willReturn(response);

    mockMvc
        .perform(
            patch("/friendships/{friendshipId}/respond", friendshipId)
                .with(user(USER_ID))
                .param("action", "ACCEPTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void should_return_401_when_responding_without_auth() throws Exception {
    UUID friendshipId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/friendships/{friendshipId}/respond", friendshipId).param("action", "ACCEPTED"))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region deleteFriendship

  @Test
  void should_return_204_when_friendship_deleted() throws Exception {
    UUID friendshipId = UUID.randomUUID();

    mockMvc
        .perform(delete("/friendships/{friendshipId}", friendshipId).with(user(USER_ID)))
        .andExpect(status().isNoContent());

    verify(friendshipService).deleteFriendship(friendshipId, UUID.fromString(USER_ID));
  }

  @Test
  void should_return_401_when_deleting_without_auth() throws Exception {
    UUID friendshipId = UUID.randomUUID();

    mockMvc
        .perform(delete("/friendships/{friendshipId}", friendshipId))
        .andExpect(status().isUnauthorized());
  }

  // endregion

  // region getFriends

  @Test
  void should_return_200_with_friends_list() throws Exception {
    UUID friendshipId = UUID.randomUUID();
    UUID friendUserId = UUID.randomUUID();
    FriendshipUserDto friendUser = new FriendshipUserDto(friendUserId, "John", null);
    FriendResponse friend = new FriendResponse(friendshipId, friendUser);

    given(friendshipService.getFriends(UUID.fromString(USER_ID))).willReturn(List.of(friend));

    mockMvc
        .perform(get("/friendships").with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].friendshipId").value(friendshipId.toString()))
        .andExpect(jsonPath("$[0].user.id").value(friendUserId.toString()))
        .andExpect(jsonPath("$[0].user.displayName").value("John"));
  }

  @Test
  void should_return_401_when_getting_friends_without_auth() throws Exception {
    mockMvc.perform(get("/friendships")).andExpect(status().isUnauthorized());
  }

  // endregion

  // region getIncomingFriendshipRequests

  @Test
  void should_return_200_with_incoming_requests() throws Exception {
    UUID friendshipId = UUID.randomUUID();
    FriendshipUserDto userDto = new FriendshipUserDto(UUID.randomUUID(), "John", null);
    PendingFriendshipRequestsResponse response =
        new PendingFriendshipRequestsResponse(friendshipId, userDto, Instant.now());

    given(friendshipService.getIncomingFriendshipsRequest(UUID.fromString(USER_ID)))
        .willReturn(List.of(response));

    mockMvc
        .perform(get("/friendships/incoming").with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].friendshipId").value(friendshipId.toString()))
        .andExpect(jsonPath("$[0].user.displayName").value("John"));
  }

  @Test
  void should_return_401_when_getting_incoming_without_auth() throws Exception {
    mockMvc.perform(get("/friendships/incoming")).andExpect(status().isUnauthorized());
  }

  // endregion

  // region getOutgoingFriendshipRequests

  @Test
  void should_return_200_with_outgoing_requests() throws Exception {
    UUID friendshipId = UUID.randomUUID();
    FriendshipUserDto userDto = new FriendshipUserDto(UUID.randomUUID(), "Jane", null);
    PendingFriendshipRequestsResponse response =
        new PendingFriendshipRequestsResponse(friendshipId, userDto, Instant.now());

    given(friendshipService.getOutgoingFriendshipsRequest(UUID.fromString(USER_ID)))
        .willReturn(List.of(response));

    mockMvc
        .perform(get("/friendships/outgoing").with(user(USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].friendshipId").value(friendshipId.toString()))
        .andExpect(jsonPath("$[0].user.displayName").value("Jane"));
  }

  @Test
  void should_return_401_when_getting_outgoing_without_auth() throws Exception {
    mockMvc.perform(get("/friendships/outgoing")).andExpect(status().isUnauthorized());
  }

  // endregion
}
