// backendsrc/main/java/com/example/controller/FeedbackController.java
package com.example.controller;

import com.example.dto.FeedbackRequest;
import com.example.entity.Ride;
import com.example.entity.User;
import com.example.repository.RideRepository;
import com.example.repository.UserRepository;
import com.example.service.FeedbackService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
     private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);


    private final FeedbackService feedbackService;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;

    public FeedbackController(
            FeedbackService feedbackService,
            RideRepository rideRepository,
            UserRepository userRepository
    ) {
        this.feedbackService = feedbackService;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/{rideId}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> submitFeedback(
        @PathVariable Long rideId,
        @RequestBody FeedbackRequest request,
        Authentication auth) {
    
    logger.info("=== FEEDBACK ENDPOINT START === rideId={}, auth={}", rideId, auth.getName());

    if (auth == null || !auth.isAuthenticated()) {
        logger.warn("FAIL: Not authenticated");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (request == null || request.getComment() == null || request.getComment().isBlank()) {
        logger.warn("FAIL: Invalid request. request={}, comment={}", 
                request != null ? "not null" : "null",
                request != null && request.getComment() != null ? "not blank" : "blank");
        return ResponseEntity.badRequest().body("Comment is required");
    }

    User user = userRepository.findByUsername(auth.getName());
    if (user == null) {
        logger.warn("FAIL: User not found by username={}", auth.getName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
    }
    logger.info("User found. userId={}", user.getId());

    Ride ride = rideRepository.findById(rideId).orElse(null);
    if (ride == null) {
        logger.warn("FAIL: Ride not found. rideId={}", rideId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ride not found");
    }
    logger.info("Ride found. rideUserId={}, status={}", ride.getUserId(), ride.getStatus());

    if (!ride.getUserId().equals(user.getId())) {
        logger.warn("FAIL: Ownership check. rideUserId={}, userId={}", ride.getUserId(), user.getId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this ride");
    }
    logger.info("Ownership verified");

    try {
        feedbackService.submitFeedback(user.getId(), rideId, request.getComment());
        logger.info("=== FEEDBACK ENDPOINT SUCCESS ===");
        return ResponseEntity.ok().build();
    } catch (Exception e) {
        logger.error("=== FEEDBACK ENDPOINT FAILED ===", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

}
