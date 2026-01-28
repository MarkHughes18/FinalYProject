package com.example.backend.files;

import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserProfileResponse(
            String fullName,
            String email,
            String dob) {
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(user -> ResponseEntity.ok(
                        new UserProfileResponse(
                                user.getFullName(),
                                user.getEmail(),
                                user.getDob())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
