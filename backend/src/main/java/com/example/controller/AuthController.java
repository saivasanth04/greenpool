package com.example.controller;

import com.example.entity.User;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.repository.UserRepository;
import com.example.service.MinioService;
import com.example.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final MinioService minioService;

    public AuthController(UserService userService,
                          UserRepository userRepository,
                          MinioService minioService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.minioService = minioService;
    }

   @PostMapping("/signup")
public ResponseEntity<?> signup(@RequestParam("username") String username,
                                @RequestParam("password") String password,
                                @RequestParam("profilePicture") MultipartFile profilePicture,
                                @RequestParam(value = "phoneNumber", required = false) String phoneNumber
    ) {
    try {
        if (profilePicture.isEmpty())
            return ResponseEntity.badRequest().body("Profile picture required");

        String objectName = userService.signupAndReturnObjectName(username, password, profilePicture, phoneNumber);
        if (objectName == null)
            return ResponseEntity.badRequest().body("No person detected or user exists");

        return ResponseEntity.ok(Map.of(
                "message", "Signup successful",
                "avatar", objectName));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Signup failed: " + e.getMessage());
    }
}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = userService.login(request.getUsername(), request.getPassword());
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        User user = userRepository.findByUsername(request.getUsername());
        String avatar = user != null ? user.getProfilePictureUrl() : "";
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(10 * 60 * 60); // 10h
        response.addCookie(cookie);
        return ResponseEntity.ok(new LoginResponse(null, avatar));
    }

    @GetMapping("/me")
public ResponseEntity<Map<String, Object>> getCurrentUser() {
    try {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return ResponseEntity.ok(Map.of(
            "username", user.getUsername(),
            "trustScore", user.getTrustScore(),
            "avatar", user.getProfilePictureUrl(),
            "phoneNumber", user.getPhoneNumber()
        ));
    } catch (UsernameNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error fetching user data"));
    }
}
}