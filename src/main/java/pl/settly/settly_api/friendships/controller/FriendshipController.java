package pl.settly.settly_api.friendships.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.friendships.dto.RequestFriendshipRequest;
import pl.settly.settly_api.friendships.dto.RequestFriendshipResponse;
import pl.settly.settly_api.friendships.service.FriendshipService;

@RestController
@RequestMapping("/friendships")
public class FriendshipController {
    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/request")
    public ResponseEntity<RequestFriendshipResponse> requestFriendship(
            @Valid @RequestBody RequestFriendshipRequest requestFriendshipRequest,
            Authentication authentication) {
        return ResponseEntity.ok(
                friendshipService.requestFriendship(
                        requestFriendshipRequest, UUID.fromString(authentication.getName())));
    }

    @PatchMapping("/{friendshipId}/respond")
    public ResponseEntity<RequestFriendshipResponse> respondToFriendship(
            @PathVariable UUID friendshipId, @RequestParam String action, Authentication authentication) {
        return ResponseEntity.ok(
                friendshipService.respondToFriendship(
                        friendshipId, action, UUID.fromString(authentication.getName())));
    }
}
