package pl.settly.settly_api.auth.user.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.auth.user.dto.UserSearchResponse;
import pl.settly.settly_api.auth.user.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {
  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/search")
  public ResponseEntity<UserSearchResponse> searchByEmail(
      @RequestParam @NotBlank @Email String email) {
    return ResponseEntity.ok(userService.searchByEmail(email));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserSearchResponse> getUserById(@PathVariable UUID userId) {
    return ResponseEntity.ok(userService.getUserById(userId));
  }
}
