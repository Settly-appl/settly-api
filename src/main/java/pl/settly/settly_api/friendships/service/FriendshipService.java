package pl.settly.settly_api.friendships.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.keycloak.KeycloakAdminService;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.friendships.dto.*;
import pl.settly.settly_api.friendships.model.Friendship;
import pl.settly.settly_api.friendships.model.FriendshipStatus;
import pl.settly.settly_api.friendships.repository.FriendshipRepository;

@Service
public class FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;
  private final FriendshipMapper friendshipMapper;
  private final KeycloakAdminService keycloakAdminService;

  public FriendshipService(
      FriendshipRepository friendshipRepository,
      UserRepository userRepository,
      FriendshipMapper friendshipMapper,
      KeycloakAdminService keycloakAdminService) {
    this.friendshipRepository = friendshipRepository;
    this.userRepository = userRepository;
    this.friendshipMapper = friendshipMapper;
    this.keycloakAdminService = keycloakAdminService;
  }

  public RequestFriendshipResponse requestFriendship(
      RequestFriendshipRequest requestFriendshipRequest, UUID userId) {
    if (requestFriendshipRequest.receiverId().equals(userId)) {
      throw new IllegalArgumentException("User cannot invite himself");
    }

    User requesterUser = userRepository.getReferenceById(userId);
    User receiverUser = keycloakAdminService.syncUser(requestFriendshipRequest.receiverId());

    if (friendshipRepository.existsActiveFriendship(
        userId, requestFriendshipRequest.receiverId(), FriendshipStatus.DECLINED)) {
      throw new IllegalArgumentException("Friendship already exists");
    }

    friendshipRepository
        .findDeclinedBetween(
            userId, requestFriendshipRequest.receiverId(), FriendshipStatus.DECLINED)
        .ifPresent(friendshipRepository::delete);

    Friendship friendship =
        Friendship.builder()
            .requesterUser(requesterUser)
            .receiverUser(receiverUser)
            .status(FriendshipStatus.PENDING)
            .build();

    return friendshipMapper.toFriendshipResponse(friendshipRepository.save(friendship));
  }

  public RequestFriendshipResponse respondToFriendship(
      UUID friendshipId, String action, UUID userId) {
    Friendship friendship =
        friendshipRepository
            .findByIdAndReceiverUserId(friendshipId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship request not found."));

    FriendshipStatus status = FriendshipStatus.valueOf(action);
    if (status != FriendshipStatus.ACCEPTED && status != FriendshipStatus.DECLINED) {
      throw new IllegalArgumentException("Invalid action: " + action);
    }

    friendship.setStatus(status);

    return friendshipMapper.toFriendshipResponse(friendshipRepository.save(friendship));
  }

  public void deleteFriendship(UUID friendshipId, UUID userId) {
    Friendship friendship =
        friendshipRepository
            .findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("Friendship does not exist"));

    if (!friendship.getRequesterUser().getId().equals(userId)
        && !friendship.getReceiverUser().getId().equals(userId)) {
      throw new ResourceNotFoundException("Friendship does not exist");
    }

    friendshipRepository.delete(friendship);
  }

  public List<FriendResponse> getFriends(UUID userId) {
    return friendshipRepository.findAllFriends(userId, FriendshipStatus.ACCEPTED).stream()
        .map(
            f -> {
              User friend =
                  f.getRequesterUser().getId().equals(userId)
                      ? f.getReceiverUser()
                      : f.getRequesterUser();
              return friendshipMapper.toFriendResponse(
                  f, friendshipMapper.toFriendshipUserDto(friend));
            })
        .toList();
  }

  public List<PendingFriendshipRequestsResponse> getIncomingFriendshipsRequest(UUID userId) {
    List<Friendship> friendships =
        friendshipRepository.findAllByReceiverUserIdAndStatus(userId, FriendshipStatus.PENDING);

    return friendships.stream()
        .map(
            f ->
                friendshipMapper.toPendingResponse(
                    f, friendshipMapper.toFriendshipUserDto(f.getRequesterUser())))
        .toList();
  }

  public boolean areFriends(UUID userId, UUID friendId) {
    return friendshipRepository.existsAcceptedFriendship(
        userId, friendId, FriendshipStatus.ACCEPTED);
  }

  public List<PendingFriendshipRequestsResponse> getOutgoingFriendshipsRequest(UUID userId) {
    List<Friendship> friendships =
        friendshipRepository.findAllByRequesterUserIdAndStatus(userId, FriendshipStatus.PENDING);

    return friendships.stream()
        .map(
            f ->
                friendshipMapper.toPendingResponse(
                    f, friendshipMapper.toFriendshipUserDto(f.getReceiverUser())))
        .toList();
  }
}
