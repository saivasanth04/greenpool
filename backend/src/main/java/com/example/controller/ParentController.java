// File: C:\Projects\EchoU\backend\src\main\java\com\example\controller\ParentController.java

package com.example.controller;

import com.example.config.JwtTokenProvider;
import com.example.entity.Parent;
import com.example.entity.User;
import com.example.repository.ParentRepository;
import com.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/parent/auth")
public class ParentController {

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Parent signup: child must be logged in, and childId is set automatically
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestParam String username,
                                    @RequestParam String password,
                                    Authentication auth) {
        // 1) Require a logged-in child
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Child must be logged in to register a parent");
        }

        String childUsername = auth.getName();
        User child = userRepository.findByUsername(childUsername);
        if (child == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Child user not found");
        }

        if (child.getParentId() != null) {
            return ResponseEntity.badRequest()
                    .body("Child already linked to a parent");
        }

        // 2) Create parent and link to this child
        Parent parent = new Parent();
        parent.setUsername(username);
        parent.setPassword(passwordEncoder.encode(password));
        parent.setChildId(child.getId());   // ID comes from backend, not from form
        parentRepository.save(parent);

        child.setParentId(parent.getId());
        userRepository.save(child);

        return ResponseEntity.ok(Map.of("message", "Parent registered"));
    }

    // Parent login: validate against parents table and issue JWT with PARENT role
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username,
                                   @RequestParam String password,
                                   HttpServletResponse response) {
        // 1) Validate parent credentials against parents table
        Parent parent = parentRepository.findByUsername(username);
        if (parent == null || !passwordEncoder.matches(password, parent.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2) Build Spring Security UserDetails with PARENT role
        UserDetails parentDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(parent.getUsername())
                        .password(parent.getPassword())  // hashed, not used at JWT validation time
                        .authorities("PARENT")
                        .build();

        // 3) Generate JWT using JwtTokenProvider
        String token = jwtTokenProvider.generateToken(parentDetails);  // role=PARENT claim
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(36000);
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "Logged in"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentParent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Parent parent = parentRepository.findByUsername(auth.getName());
        if (parent == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "username", parent.getUsername(),
                "childLat", parent.getChildLat(),
                "childLon", parent.getChildLon(),
                "rideStatus", parent.getRideStatus(),
                "partnerUsername", parent.getPartnerUsername(),
                "partnerPhone", parent.getPartnerPhone(),
                "childRideId", parent.getChildRideId()
        ));
    }
}
