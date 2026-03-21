package pl.settly.settly_api.friendships;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.time.Instant;
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
}
