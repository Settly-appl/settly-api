package pl.settly.settly_api.friendships.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.friendships.model.Friendship;
import pl.settly.settly_api.friendships.model.FriendshipStatus;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    Optional<Friendship> findByIdAndReceiverUserId(UUID id, UUID receiverId);

    List<Friendship> findAllByReceiverUserIdAndStatus(UUID receiverId, FriendshipStatus status);

    List<Friendship> findAllByRequesterUserIdAndStatus(UUID requesterId, FriendshipStatus status);

    @Query(
            "SELECT COUNT(f) > 0 FROM Friendship f WHERE f.status != :declined AND "
                    + "(f.requesterUser.id = :userId AND f.receiverUser.id = :receiverId OR "
                    + "f.requesterUser.id = :receiverId AND f.receiverUser.id = :userId)")
    boolean existsActiveFriendship(
            @Param("userId") UUID userId,
            @Param("receiverId") UUID receiverId,
            @Param("declined") FriendshipStatus declined);

    @Query(
            "SELECT f FROM Friendship f WHERE f.status = :declined AND "
                    + "(f.requesterUser.id = :userId AND f.receiverUser.id = :receiverId OR "
                    + "f.requesterUser.id = :receiverId AND f.receiverUser.id = :userId)")
    Optional<Friendship> findDeclinedBetween(
            @Param("userId") UUID userId,
            @Param("receiverId") UUID receiverId,
            @Param("declined") FriendshipStatus declined);
}
