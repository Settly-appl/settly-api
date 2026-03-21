package pl.settly.settly_api.friendships.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.friendships.model.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    boolean existsByRequesterUserIdAndReceiverUserIdOrRequesterUserIdAndReceiverUserId(
            UUID requesterId1, UUID receiverId1, UUID requesterId2, UUID receiverId2);

    Optional<Friendship> findByIdAndReceiverUserId(UUID id, UUID receiverId);
}
