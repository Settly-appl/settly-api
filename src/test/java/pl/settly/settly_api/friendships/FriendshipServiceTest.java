package pl.settly.settly_api.friendships;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
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
import pl.settly.settly_api.friendships.dto.FriendshipUserDto;
import pl.settly.settly_api.friendships.dto.PendingFriendshipRequestsResponse;
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

    // region requestFriendship

    @Test
    void should_throw_when_user_invites_himself() {
        RequestFriendshipRequest request = new RequestFriendshipRequest(userId);

        assertThatThrownBy(() -> friendshipService.requestFriendship(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User cannot invite himself");
    }

    @Test
    void should_throw_when_active_friendship_already_exists() {
        RequestFriendshipRequest request = new RequestFriendshipRequest(receiverId);

        given(
                        friendshipRepository.existsActiveFriendship(
                                userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(true);

        assertThatThrownBy(() -> friendshipService.requestFriendship(request, userId))
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
                        friendshipRepository.existsActiveFriendship(
                                userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(false);
        given(friendshipRepository.findDeclinedBetween(userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(Optional.empty());
        given(friendshipRepository.save(any(Friendship.class))).willReturn(savedFriendship);
        given(friendshipMapper.toFriendshipResponse(savedFriendship)).willReturn(expectedResponse);

        RequestFriendshipResponse response = friendshipService.requestFriendship(request, userId);

        assertThat(response.status()).isEqualTo(FriendshipStatus.PENDING);
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void should_delete_declined_friendship_and_create_new_request() {
        RequestFriendshipRequest request = new RequestFriendshipRequest(receiverId);
        Friendship declinedFriendship =
                Friendship.builder().id(UUID.randomUUID()).status(FriendshipStatus.DECLINED).build();
        Friendship savedFriendship = Friendship.builder().status(FriendshipStatus.PENDING).build();
        RequestFriendshipResponse expectedResponse =
                new RequestFriendshipResponse(FriendshipStatus.PENDING, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        given(keycloakAdminService.syncUser(receiverId)).willReturn(new User());
        given(
                        friendshipRepository.existsActiveFriendship(
                                userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(false);
        given(friendshipRepository.findDeclinedBetween(userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(Optional.of(declinedFriendship));
        given(friendshipRepository.save(any(Friendship.class))).willReturn(savedFriendship);
        given(friendshipMapper.toFriendshipResponse(savedFriendship)).willReturn(expectedResponse);

        friendshipService.requestFriendship(request, userId);

        verify(friendshipRepository).delete(declinedFriendship);
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void should_not_delete_anything_when_no_declined_friendship_exists() {
        RequestFriendshipRequest request = new RequestFriendshipRequest(receiverId);
        Friendship savedFriendship = Friendship.builder().status(FriendshipStatus.PENDING).build();

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        given(keycloakAdminService.syncUser(receiverId)).willReturn(new User());
        given(
                        friendshipRepository.existsActiveFriendship(
                                userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(false);
        given(friendshipRepository.findDeclinedBetween(userId, receiverId, FriendshipStatus.DECLINED))
                .willReturn(Optional.empty());
        given(friendshipRepository.save(any(Friendship.class))).willReturn(savedFriendship);
        given(friendshipMapper.toFriendshipResponse(savedFriendship))
                .willReturn(new RequestFriendshipResponse(FriendshipStatus.PENDING, null));

        friendshipService.requestFriendship(request, userId);

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }

    // endregion

    // region respondToFriendship

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

    // endregion

    // region deleteFriendship

    @Test
    void should_delete_friendship_as_requester() {
        UUID friendshipId = UUID.randomUUID();
        User requester = new User();
        requester.setId(userId);
        User receiver = new User();
        receiver.setId(receiverId);
        Friendship friendship =
                Friendship.builder()
                        .id(friendshipId)
                        .requesterUser(requester)
                        .receiverUser(receiver)
                        .build();

        given(friendshipRepository.findById(friendshipId)).willReturn(Optional.of(friendship));

        friendshipService.deleteFriendship(friendshipId, userId);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void should_delete_friendship_as_receiver() {
        UUID friendshipId = UUID.randomUUID();
        User requester = new User();
        requester.setId(userId);
        User receiver = new User();
        receiver.setId(receiverId);
        Friendship friendship =
                Friendship.builder()
                        .id(friendshipId)
                        .requesterUser(requester)
                        .receiverUser(receiver)
                        .build();

        given(friendshipRepository.findById(friendshipId)).willReturn(Optional.of(friendship));

        friendshipService.deleteFriendship(friendshipId, receiverId);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void should_throw_when_friendship_not_found_on_delete() {
        UUID friendshipId = UUID.randomUUID();

        given(friendshipRepository.findById(friendshipId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendshipService.deleteFriendship(friendshipId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Friendship does not exist");
    }

    @Test
    void should_throw_when_user_is_not_participant_on_delete() {
        UUID friendshipId = UUID.randomUUID();
        User requester = new User();
        requester.setId(UUID.randomUUID());
        User receiver = new User();
        receiver.setId(UUID.randomUUID());
        Friendship friendship =
                Friendship.builder()
                        .id(friendshipId)
                        .requesterUser(requester)
                        .receiverUser(receiver)
                        .build();

        given(friendshipRepository.findById(friendshipId)).willReturn(Optional.of(friendship));

        assertThatThrownBy(() -> friendshipService.deleteFriendship(friendshipId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Friendship does not exist");
        verify(friendshipRepository, never()).delete(any());
    }

    // endregion

    // region getIncomingFriendshipsRequest

    @Test
    void should_return_incoming_friendship_requests() {
        User requester = new User();
        Friendship friendship =
                Friendship.builder()
                        .id(UUID.randomUUID())
                        .requesterUser(requester)
                        .status(FriendshipStatus.PENDING)
                        .createdAt(Instant.now())
                        .build();
        FriendshipUserDto userDto = new FriendshipUserDto("John", null);
        PendingFriendshipRequestsResponse expectedResponse =
                new PendingFriendshipRequestsResponse(
                        friendship.getId(), userDto, friendship.getCreatedAt());

        given(friendshipRepository.findAllByReceiverUserIdAndStatus(userId, FriendshipStatus.PENDING))
                .willReturn(List.of(friendship));
        given(friendshipMapper.toFriendshipUserDto(requester)).willReturn(userDto);
        given(friendshipMapper.toPendingResponse(friendship, userDto)).willReturn(expectedResponse);

        List<PendingFriendshipRequestsResponse> result =
                friendshipService.getIncomingFriendshipsRequest(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);
    }

    @Test
    void should_return_empty_list_when_no_incoming_requests() {
        given(friendshipRepository.findAllByReceiverUserIdAndStatus(userId, FriendshipStatus.PENDING))
                .willReturn(List.of());

        List<PendingFriendshipRequestsResponse> result =
                friendshipService.getIncomingFriendshipsRequest(userId);

        assertThat(result).isEmpty();
    }

    // endregion

    // region getOutgoingFriendshipsRequest

    @Test
    void should_return_outgoing_friendship_requests() {
        User receiver = new User();
        Friendship friendship =
                Friendship.builder()
                        .id(UUID.randomUUID())
                        .receiverUser(receiver)
                        .status(FriendshipStatus.PENDING)
                        .createdAt(Instant.now())
                        .build();
        FriendshipUserDto userDto = new FriendshipUserDto("Jane", null);
        PendingFriendshipRequestsResponse expectedResponse =
                new PendingFriendshipRequestsResponse(
                        friendship.getId(), userDto, friendship.getCreatedAt());

        given(friendshipRepository.findAllByRequesterUserIdAndStatus(userId, FriendshipStatus.PENDING))
                .willReturn(List.of(friendship));
        given(friendshipMapper.toFriendshipUserDto(receiver)).willReturn(userDto);
        given(friendshipMapper.toPendingResponse(friendship, userDto)).willReturn(expectedResponse);

        List<PendingFriendshipRequestsResponse> result =
                friendshipService.getOutgoingFriendshipsRequest(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedResponse);
    }

    @Test
    void should_return_empty_list_when_no_outgoing_requests() {
        given(friendshipRepository.findAllByRequesterUserIdAndStatus(userId, FriendshipStatus.PENDING))
                .willReturn(List.of());

        List<PendingFriendshipRequestsResponse> result =
                friendshipService.getOutgoingFriendshipsRequest(userId);

        assertThat(result).isEmpty();
    }

    // endregion
}
