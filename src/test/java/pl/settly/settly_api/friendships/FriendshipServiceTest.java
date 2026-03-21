package pl.settly.settly_api.friendships;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.keycloak.KeycloakAdminService;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.friendships.dto.FriendshipMapper;
import pl.settly.settly_api.friendships.dto.RequestFriendshipRequest;
import pl.settly.settly_api.friendships.dto.RequestFriendshipResponse;
import pl.settly.settly_api.friendships.model.Friendship;
import pl.settly.settly_api.friendships.model.FriendshipStatus;
import pl.settly.settly_api.friendships.repository.FriendshipRepository;
import pl.settly.settly_api.friendships.service.FriendshipService;

@ExtendWith(MockitoExtension.class)
public class FriendshipServiceTest {

    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock FriendshipMapper friendshipMapper;
    @Mock KeycloakAdminService keycloakAdminService;

    @InjectMocks FriendshipService friendshipService;

    private final UUID userId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();

    @Test
    void should_throw_when_user_invites_himself() {
        RequestFriendshipRequest requestFriendshipRequest = new RequestFriendshipRequest(userId);

        assertThatThrownBy(() -> friendshipService.requestFriendship(requestFriendshipRequest, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User cannot invite himself");
    }

    @Test
    void should_throw_when_friendship_already_exists() {
        RequestFriendshipRequest requestFriendshipRequest = new RequestFriendshipRequest(receiverId);

        given(
                        friendshipRepository
                                .existsByRequesterUserIdAndReceiverUserIdOrRequesterUserIdAndReceiverUserId(
                                        userId, receiverId, receiverId, userId))
                .willReturn(true);

        assertThatThrownBy(() -> friendshipService.requestFriendship(requestFriendshipRequest, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Friendship already exists");
    }

    @Test
    void should_request_friendship_successfully() {
        RequestFriendshipRequest request = new RequestFriendshipRequest(receiverId);
        User requester = new User();
        User receiver = new User();
        Friendship savedFriendship =
                Friendship.builder()
                        .requesterUser(requester)
                        .receiverUser(receiver)
                        .status(FriendshipStatus.PENDING)
                        .build();
        RequestFriendshipResponse expectedResponse =
                new RequestFriendshipResponse(FriendshipStatus.PENDING, null);

        given(userRepository.getReferenceById(userId)).willReturn(requester);
        given(keycloakAdminService.syncUser(receiverId)).willReturn(receiver);
        given(
                        friendshipRepository
                                .existsByRequesterUserIdAndReceiverUserIdOrRequesterUserIdAndReceiverUserId(
                                        userId, receiverId, receiverId, userId))
                .willReturn(false);
        given(friendshipRepository.save(any(Friendship.class))).willReturn(savedFriendship);
        given(friendshipMapper.toFriendshipResponse(savedFriendship)).willReturn(expectedResponse);

        RequestFriendshipResponse response = friendshipService.requestFriendship(request, userId);

        assertThat(response).isEqualTo(expectedResponse);
        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void should_throw_when_friendship_request_not_found() {
        UUID friendshipId = UUID.randomUUID();

        given(friendshipRepository.findByIdAndReceiverUserId(friendshipId, userId))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () -> friendshipService.respondToFriendship(friendshipId, "ACCEPTED", userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Friendship request not found.");
    }

    @Test
    void should_accept_friendship_successfully() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship =
                Friendship.builder().id(friendshipId).status(FriendshipStatus.PENDING).build();
        RequestFriendshipResponse expectedResponse =
                new RequestFriendshipResponse(FriendshipStatus.ACCEPTED, null);

        given(friendshipRepository.findByIdAndReceiverUserId(friendshipId, userId))
                .willReturn(Optional.of(friendship));
        given(friendshipRepository.save(friendship)).willReturn(friendship);
        given(friendshipMapper.toFriendshipResponse(friendship)).willReturn(expectedResponse);

        RequestFriendshipResponse response =
                friendshipService.respondToFriendship(friendshipId, "ACCEPTED", userId);

        assertThat(response.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(friendshipRepository).save(friendship);
    }

    @Test
    void should_decline_friendship_successfully() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship =
                Friendship.builder().id(friendshipId).status(FriendshipStatus.PENDING).build();
        RequestFriendshipResponse expectedResponse =
                new RequestFriendshipResponse(FriendshipStatus.DECLINED, null);

        given(friendshipRepository.findByIdAndReceiverUserId(friendshipId, userId))
                .willReturn(Optional.of(friendship));
        given(friendshipRepository.save(friendship)).willReturn(friendship);
        given(friendshipMapper.toFriendshipResponse(friendship)).willReturn(expectedResponse);

        RequestFriendshipResponse response =
                friendshipService.respondToFriendship(friendshipId, "DECLINED", userId);

        assertThat(response.status()).isEqualTo(FriendshipStatus.DECLINED);
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
        verify(friendshipRepository).save(friendship);
    }

    @Test
    void should_throw_when_action_is_pending() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship =
                Friendship.builder().id(friendshipId).status(FriendshipStatus.PENDING).build();

        given(friendshipRepository.findByIdAndReceiverUserId(friendshipId, userId))
                .willReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.respondToFriendship(friendshipId, "PENDING", userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid action");
    }

    @Test
    void should_throw_when_action_is_unknown() {
        UUID friendshipId = UUID.randomUUID();
        Friendship friendship =
                Friendship.builder().id(friendshipId).status(FriendshipStatus.PENDING).build();

        given(friendshipRepository.findByIdAndReceiverUserId(friendshipId, userId))
                .willReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.respondToFriendship(friendshipId, "INVALID", userId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
