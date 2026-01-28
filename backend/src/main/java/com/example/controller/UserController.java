// New: backend/src/main/java/com/example/controller/UserController.java
package com.example.controller;

import com.example.dto.UserProfileResponse;
import com.example.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(new UserProfileResponse(u.getId(), u.getUsername(), u.getTrustScore(), u.getProfilePictureUrl(),u.getPhoneNumber(),u.getParentId())))
                .orElse(ResponseEntity.notFound().build());
    }
}