package pl.settly.settly_api.friendships.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.keycloak.KeycloakAdminService;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.friendships.dto.FriendshipMapper;
import pl.settly.settly_api.friendships.dto.RequestFriendshipRequest;
import pl.settly.settly_api.friendships.dto.RequestFriendshipResponse;
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

        if (friendshipRepository
                .existsByRequesterUserIdAndReceiverUserIdOrRequesterUserIdAndReceiverUserId(
                        userId,
                        requestFriendshipRequest.receiverId(),
                        requestFriendshipRequest.receiverId(),
                        userId)) {
            throw new IllegalArgumentException("Friendship already exists");
        }

        Friendship friendship =
                Friendship.builder()
                        .requesterUser(requesterUser)
                        .receiverUser(receiverUser)
                        .status(FriendshipStatus.PENDING)
                        .build();

        return friendshipMapper.toFriendshipResponse(friendshipRepository.save(friendship));
    }
}
