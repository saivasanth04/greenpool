// backend/src/main/java/com/example/controller/RideController.java (Moved geocode/reverse to service, updated RideResponse construction, added match request endpoints)
package com.example.controller;

import com.example.entity.Ride.RideStatus;
import com.example.dto.RideMatchRequestResponse;
import com.example.dto.RideMatchResponse;
import com.example.dto.RideRequest;
import com.example.dto.RideResponse;
import com.example.entity.Ride;
import com.example.entity.Parent; // CHANGE: Added import
import com.example.entity.Ride.RideStatus;

import com.example.repository.ParentRepository; // CHANGE: Added import
import com.example.entity.RideMatch;
import com.example.entity.RideMatchRequest;
import com.example.entity.User;
import com.example.repository.RideMatchRepository;
import com.example.repository.RideMatchRequestRepository;
import com.example.repository.RideRepository;
import com.example.repository.UserRepository;
import com.example.repository.FeedbackRepository; // CHANGE: Added import
import com.example.service.RideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rides")
public class RideController {
    private static final Logger logger = LoggerFactory.getLogger(RideController.class);
    private final RideService rideService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RideMatchRepository rideMatchRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final RideMatchRequestRepository rideMatchRequestRepository;
    private final ParentRepository parentRepository;
    private final FeedbackRepository feedbackRepository; // CHANGE: Added field

    public RideController(RideService rideService, RideRepository rideRepository, UserRepository userRepository,
            RideMatchRepository rideMatchRepository, RideMatchRequestRepository rideMatchRequestRepository,
            RestTemplate restTemplate, ParentRepository parentRepository,
            RedisTemplate<String, Object> redisTemplate, FeedbackRepository feedbackRepository) { // CHANGE: Added param
        this.rideService = rideService;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.rideMatchRepository = rideMatchRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.rideMatchRequestRepository = rideMatchRequestRepository;
        this.parentRepository = parentRepository;
        this.feedbackRepository = feedbackRepository; // CHANGE: Assigned // CHANGE: Assigned
    }

    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RideResponse> requestRide(@RequestBody RideRequest request) {
        try {
            Ride ride = rideService.createRide(request);
            RideResponse response = new RideResponse(
                    ride.getId(),
                    ride.getPickupLat(),
                    ride.getPickupLon(),
                    ride.getDropoffLat(),
                    ride.getDropoffLon(),
                    ride.getStatus().name(),
                    ride.getCarbonEstimate(),
                    ride.getH3Index(),
                    ride.getUserId(),
                    ride.getPickupAddress(),
                    ride.getDropoffAddress());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid ride request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            logger.error("Ride request failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/geocode")
    public ResponseEntity<Map<String, Double>> geocode(@RequestParam String address) {
        Map<String, Double> location = rideService.geocode(address);
        if (location == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(location);
    }

    @GetMapping("/reverse-geocode")
    public ResponseEntity<Map<String, String>> reverseGeocode(@RequestParam double lat, @RequestParam double lon) {
        Map<String, String> location = rideService.reverseGeocode(lat, lon);
        return ResponseEntity.ok(location);
    }

    @PostMapping("/match/start/{requestId}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<String> startMatch(@PathVariable Long requestId, Authentication auth) {
        try {
            logger.info("Start match called: requestId={}, user={}", requestId, auth.getName());
            RideMatchRequest request = rideMatchRequestRepository.findById(requestId).orElseThrow();
            logger.info("Request status={}, fromConfirmed={}, toConfirmed={}",
                    request.getStatus(), request.isStartConfirmedFrom(), request.isStartConfirmedTo());
            User user = userRepository.findByUsername(auth.getName());
            Ride fromRide = rideRepository.findById(request.getFromRideId()).orElseThrow();
            Ride toRide = rideRepository.findById(request.getToRideId()).orElseThrow();

            Ride userRide;
            if (fromRide.getUserId().equals(user.getId())) {
                userRide = fromRide;
            } else if (toRide.getUserId().equals(user.getId())) {
                userRide = toRide;
            } else {
                logger.info("User {} does not own either ride in request {}", user.getUsername(), requestId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (request.getStatus() == RideStatus.CONFIRMED) {
                if (userRide.getId().equals(request.getFromRideId())) {
                    request.setStartConfirmedFrom(true);
                } else {
                    request.setStartConfirmedTo(true);
                }
                rideMatchRequestRepository.save(request);
                if (request.isStartConfirmedFrom() && request.isStartConfirmedTo()) {
                    logger.info("Both confirmed - setting INPROGRESS");
                    rideRepository.findById(request.getFromRideId()).ifPresent(r -> {
                        r.setStatus(RideStatus.IN_PROGRESS);
                        rideRepository.save(r);
                    });
                    rideRepository.findById(request.getToRideId()).ifPresent(r -> {
                        r.setStatus(RideStatus.IN_PROGRESS);
                        rideRepository.save(r);
                    });
                    return ResponseEntity.ok("Journey started for both!");
                }
                return ResponseEntity.ok("Start confirmed. Waiting for partner.");
            }
            logger.info("Request status EXACT: '{}'", request.getStatus());
            return ResponseEntity.badRequest().body("Invalid status");
        } catch (Exception e) {
            logger.error("Failed to start match: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{rideId}/location")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateLocation(@PathVariable Long rideId, @RequestParam double lat,
            @RequestParam double lon, Authentication auth) {
        Ride ride = rideRepository.findById(rideId).orElseThrow();
        ride.setCurrentLat(lat);
        ride.setCurrentLon(lon);
        rideRepository.save(ride);
        User child = userRepository.findByUsername(auth.getName());
        if (child.getParentId() != null) {
            Parent parent = parentRepository.findById(child.getParentId()).orElse(null);
            if (parent != null) {
                parent.setChildLat(lat);
                parent.setChildLon(lon);
                parent.setRideStatus(ride.getStatus().name());
                parent.setChildRideId(rideId);

                // FIXED: Find active/completed match requests where this ride is either 'from'
                // or 'to'
                // Query for CONFIRMED (covers IN_PROGRESS rides) and COMPLETED statuses
                List<RideMatchRequest> matches = new ArrayList<>();
                matches.addAll(
                        rideMatchRequestRepository.findByFromRideIdInAndStatus(List.of(rideId), RideStatus.CONFIRMED));
                matches.addAll(
                        rideMatchRequestRepository.findByToRideIdInAndStatus(List.of(rideId), RideStatus.CONFIRMED));
                matches.addAll(
                        rideMatchRequestRepository.findByFromRideIdInAndStatus(List.of(rideId), RideStatus.COMPLETED));
                matches.addAll(
                        rideMatchRequestRepository.findByToRideIdInAndStatus(List.of(rideId), RideStatus.COMPLETED));

                // If any match found (assume at most one active match per ride)
                RideMatchRequest matchReq = !matches.isEmpty() ? matches.get(0) : null;
                if (matchReq != null && ("IN_PROGRESS".equals(ride.getStatus().name())
                        || "COMPLETED".equals(ride.getStatus().name()))) {
                    // Determine partner ride ID (the other one in the match)
                    Long partnerRideId = rideId.equals(matchReq.getFromRideId()) ? matchReq.getToRideId()
                            : matchReq.getFromRideId();
                    Ride partnerRide = rideRepository.findById(partnerRideId).orElse(null);
                    if (partnerRide != null) {
                        User partnerUser = userRepository.findById(partnerRide.getUserId()).orElse(null);
                        if (partnerUser != null) {
                            parent.setPartnerUsername(partnerUser.getUsername());
                            parent.setPartnerPhone(partnerUser.getPhoneNumber());
                        }
                    }
                }
                parentRepository.save(parent);
            }
        }
        return ResponseEntity.ok().build();
    }
    // inside RideController

    @PostMapping("/match/end/{requestId}")
@PreAuthorize("isAuthenticated()")
@Transactional
public ResponseEntity<?> endMatch(@PathVariable Long requestId, Authentication auth) {
    try {
        RideMatchRequest request = rideMatchRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Match request not found"));

        User user = userRepository.findByUsername(auth.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Ride fromRide = rideRepository.findById(request.getFromRideId()).orElseThrow();
        Ride toRide = rideRepository.findById(request.getToRideId()).orElseThrow();

        // Determine which ride belongs to current user
        Ride userRide;
        boolean isFromUser = fromRide.getUserId().equals(user.getId());
        boolean isToUser = toRide.getUserId().equals(user.getId());
        
        if (isFromUser) {
            userRide = fromRide;
        } else if (isToUser) {
            userRide = toRide;
        } else {
            logger.warn("User {} does not own either ride in request {}", user.getUsername(), requestId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this ride");
        }

        // Check if already completed by both - idempotent check
        if (request.getStatus() == RideStatus.COMPLETED) {
            logger.info("Request {} already completed, redirecting to feedback", requestId);
            return ResponseEntity.ok(Map.of(
                "completedForBoth", true,
                "message", "Ride already completed!",
                "rideId", userRide.getId(),
                "redirectToFeedback", true
            ));
        }

        // Allow end while status is CONFIRMED or IN_PROGRESS
        if (request.getStatus() != RideStatus.CONFIRMED && request.getStatus() != RideStatus.IN_PROGRESS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + request.getStatus()));
        }

        // Mark which side confirmed
        if (isFromUser) {
            request.setEndConfirmedFrom(true);
            logger.info("User {} (from) confirmed end", user.getUsername());
        } else {
            request.setEndConfirmedTo(true);
            logger.info("User {} (to) confirmed end", user.getUsername());
        }
        
        rideMatchRequestRepository.save(request);

        boolean bothEnded = request.isEndConfirmedFrom() && request.isEndConfirmedTo();

        if (bothEnded) {
            // Mark both rides completed
            fromRide.setStatus(RideStatus.COMPLETED);
            toRide.setStatus(RideStatus.COMPLETED);
            rideRepository.save(fromRide);
            rideRepository.save(toRide);

            // Mark match request COMPLETED
            request.setStatus(RideStatus.COMPLETED);
            rideMatchRequestRepository.save(request);

            logger.info("Journey completed for both. requestId={}, fromUser={}, toUser={}", 
                requestId, fromRide.getUserId(), toRide.getUserId());
            
            return ResponseEntity.ok(Map.of(
                "completedForBoth", true,
                "message", "Journey completed!",
                "rideId", userRide.getId(),
                "redirectToFeedback", true
            ));
        } else {
            logger.info("End confirmed by one user. Waiting for partner. requestId={}, user={}", 
                requestId, user.getUsername());
            return ResponseEntity.ok(Map.of(
                "completedForBoth", false,
                "message", "End confirmed. Waiting for partner.",
                "rideId", userRide.getId(),
                "redirectToFeedback", false
            ));
        }
    } catch (Exception e) {
        logger.error("Failed to end match {}", requestId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to end ride: " + e.getMessage()));
    }
}

    @GetMapping("/child-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getChildStatus(Authentication auth) {
        Parent parent = parentRepository.findByUsername(auth.getName());
        if (parent == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "childLat", parent.getChildLat(),
                "childLon", parent.getChildLon(),
                "rideStatus", parent.getRideStatus(),
                "partnerUsername", parent.getPartnerUsername(),
                "partnerPhone", parent.getPartnerPhone(),
                "childRideId", parent.getChildRideId()));
    }

    @GetMapping("/active")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<RideResponse> getActiveRide(Authentication auth) {
    try {
        User user = userRepository.findByUsername(auth.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = user.getId();
        
        // First check for IN_PROGRESS rides
        List<Ride> userRides = rideRepository.findByUserIdAndStatus(userId, RideStatus.IN_PROGRESS);
        
        if (userRides.isEmpty()) {
            // Check for unreviewed COMPLETED rides (that need feedback)
            List<Ride> completed = rideRepository.findByUserIdAndStatus(userId, RideStatus.COMPLETED);
            completed.sort((r1, r2) -> r2.getId().compareTo(r1.getId()));

            for (Ride r : completed) {
                // Check if feedback already submitted for this ride
                boolean hasFeedback = feedbackRepository.findByFromUserIdAndRideId(userId, r.getId()).isPresent();
                if (!hasFeedback) {
                    // Return this ride so user can give feedback
                    return ResponseEntity.ok(new RideResponse(
                        r.getId(), r.getPickupLat(), r.getPickupLon(),
                        r.getDropoffLat(), r.getDropoffLon(), r.getStatus().name(), 
                        r.getCarbonEstimate(), r.getH3Index(), r.getUserId(), 
                        r.getPickupAddress(), r.getDropoffAddress()));
                }
            }
            
            // No active or completed rides needing feedback
            return ResponseEntity.ok(null);
        }
        
        // Return the in-progress ride
        Ride ride = userRides.get(0);
        return ResponseEntity.ok(new RideResponse(
            ride.getId(), ride.getPickupLat(), ride.getPickupLon(),
            ride.getDropoffLat(), ride.getDropoffLon(), ride.getStatus().name(), 
            ride.getCarbonEstimate(), ride.getH3Index(), ride.getUserId(), 
            ride.getPickupAddress(), ride.getDropoffAddress()));
            
    } catch (Exception e) {
        logger.error("Failed to get active ride: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

    @GetMapping("/distance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> distance(
            @RequestParam double fromLat,
            @RequestParam double fromLon,
            @RequestParam double toLat,
            @RequestParam double toLon) {
        try {
            // OSRM format: lon,lat;lon,lat (NOT lat,lon) [web:25]
            String coords = String.format(Locale.US, "%f,%f;%f,%f", fromLon, fromLat, toLon, toLat);
            String url = "http://router.project-osrm.org/route/v1/driving/" + coords
                    + "?overview=false&steps=false&alternatives=false";

            Map<?, ?> osrm = restTemplate.getForObject(url, Map.class);

            double distanceMeters = 0.0;
            double durationSeconds = 0.0;

            if (osrm != null && osrm.get("routes") instanceof List<?> routes && !routes.isEmpty()) {
                Object r0 = routes.get(0);
                if (r0 instanceof Map<?, ?> route) {
                    Object distObj = route.get("distance"); // meters [web:24]
                    Object durObj = route.get("duration"); // seconds [web:24]
                    if (distObj instanceof Number)
                        distanceMeters = ((Number) distObj).doubleValue();
                    if (durObj instanceof Number)
                        durationSeconds = ((Number) durObj).doubleValue();
                }
            }

            double distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
            double durationMin = Math.round((durationSeconds / 60.0) * 10.0) / 10.0;

            return ResponseEntity.ok(Map.of(
                    "distanceKm", distanceKm,
                    "durationMin", durationMin));
        } catch (Exception e) {
            logger.error("Distance API failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("distanceKm", 0.0, "durationMin", 0.0));
        }
    }

    @GetMapping("/{rideId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RideResponse> getRide(@PathVariable Long rideId, Authentication auth) {
        try {
            Ride ride = rideRepository.findById(rideId).orElse(null);
            if (ride == null || !ride.getUserId().equals(userRepository.findByUsername(auth.getName()).getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(new RideResponse(ride.getId(), ride.getPickupLat(), ride.getPickupLon(),
                    ride.getDropoffLat(), ride.getDropoffLon(), ride.getStatus().name(), ride.getCarbonEstimate(),
                    ride.getH3Index(), ride.getUserId(), ride.getPickupAddress(), ride.getDropoffAddress()));
        } catch (Exception e) {
            logger.error("Failed to fetch ride {}: {}", rideId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RideResponse>> getUserRides(Authentication auth) {
        try {
            Long userId = userRepository.findByUsername(auth.getName()).getId();

            logger.info("Fetching rides for user '{}' (ID: {})", auth.getName(), userId);
            List<Ride> rides = rideRepository.findByUserId(userId);
            logger.info("Found {} rides for user ID {}", rides.size(), userId);
            List<RideResponse> responses = rides.stream()
                    .map(r -> new RideResponse(r.getId(), r.getPickupLat(), r.getPickupLon(), r.getDropoffLat(),
                            r.getDropoffLon(), r.getStatus().name(), r.getCarbonEstimate(), r.getH3Index(),
                            r.getUserId(), r.getPickupAddress(), r.getDropoffAddress()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Failed to fetch rides: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/match/{rideId}")
    public ResponseEntity<RideResponse> getMatchRide(@PathVariable Long rideId) {
        try {
            Ride ride = rideRepository.findById(rideId).orElse(null);
            if (ride == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(new RideResponse(ride.getId(), ride.getPickupLat(), ride.getPickupLon(),
                    ride.getDropoffLat(), ride.getDropoffLon(), ride.getStatus().name(), ride.getCarbonEstimate(),
                    ride.getH3Index(), ride.getUserId(), ride.getPickupAddress(), ride.getDropoffAddress()));
        } catch (Exception e) {
            logger.error("Failed to fetch match ride {}: {}", rideId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/matches/{rideId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RideMatchResponse>> getMatches(@PathVariable Long rideId, Authentication auth) {
        try {
            Ride ride = rideRepository.findById(rideId).orElse(null);
            if (ride == null || !ride.getUserId().equals(userRepository.findByUsername(auth.getName()).getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            List<RideMatch> matches = rideMatchRepository.findByRideId(rideId);
            List<RideMatchResponse> responses = new ArrayList<>();
            for (RideMatch match : matches) {
                responses
                        .add(new RideMatchResponse(match.getRideId(), match.getMatchedRideIds(), match.getClusterId()));
            }
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Failed to get matches for ride {}: {}", rideId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/match/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> requestMatch(@RequestBody Map<String, Long> body, Authentication auth) {
        Long rideId = body.get("rideId");
        Long matchedRideId = body.get("matchedRideId");
        try {
            Ride ride = rideRepository.findById(rideId).orElseThrow();
            Ride matchedRide = rideRepository.findById(matchedRideId).orElseThrow();
            User user = userRepository.findByUsername(auth.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!ride.getUserId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            RideMatchRequest existing = rideMatchRequestRepository.findByFromRideIdAndToRideId(rideId, matchedRideId);
            if (existing != null) {
                return ResponseEntity.badRequest().body("Request already sent");
            }
            RideMatchRequest request = new RideMatchRequest();
            request.setFromRideId(rideId);
            request.setToRideId(matchedRideId);
            request.setStatus(RideStatus.PENDING);
            rideMatchRequestRepository.save(request);
            logger.info("Match request from rideId={}, to matchedRideId={}, user={}", rideId, matchedRideId,
                    auth.getName());
            return ResponseEntity.ok("Request sent");
        } catch (Exception e) {
            logger.error("Failed to send match request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    }

    @GetMapping("/requests/incoming")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RideMatchRequestResponse>> getIncomingRequests(Authentication auth) {
        try {
            User user = userRepository.findByUsername(auth.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long userId = user.getId();
            List<Ride> userRides = rideRepository.findByUserId(userId);
            List<Long> userRideIds = userRides.stream().map(Ride::getId).collect(Collectors.toList());
            List<RideMatchRequest> requests = rideMatchRequestRepository.findByToRideIdInAndStatus(userRideIds,
                    RideStatus.PENDING);
            List<RideMatchRequestResponse> responses = requests.stream()
                    .map(r -> new RideMatchRequestResponse(r.getId(), r.getFromRideId(), r.getToRideId(),
                            r.getStatus()))
                    .collect(Collectors.toList());
            logger.info("Fetching incoming requests for userId={}, found {} requests", userId, requests.size());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Failed to fetch incoming requests: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/matches/confirmed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RideMatchRequestResponse>> getConfirmedMatches(Authentication auth) {
        try {
            User user = userRepository.findByUsername(auth.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Long userId = user.getId();
            List<Ride> userRides = rideRepository.findByUserId(userId);
            List<Long> userRideIds = userRides.stream().map(Ride::getId).collect(Collectors.toList());
            List<RideMatchRequest> requests = rideMatchRequestRepository.findByToRideIdInAndStatus(userRideIds,
                    RideStatus.CONFIRMED);
            requests.addAll(rideMatchRequestRepository.findByFromRideIdInAndStatus(userRideIds, RideStatus.CONFIRMED));
            requests = requests.stream().filter(r -> !RideStatus.COMPLETED.equals(r.getStatus()))
                    .collect(Collectors.toList()); // Filter completed
            List<RideMatchRequestResponse> responses = requests.stream()
                    .map(r -> new RideMatchRequestResponse(r.getId(), r.getFromRideId(), r.getToRideId(),
                            r.getStatus()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            logger.error("Failed to fetch confirmed matches: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/match/confirm/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> confirmMatch(@PathVariable Long requestId, Authentication auth) {
        try {
            RideMatchRequest request = rideMatchRequestRepository.findById(requestId).orElseThrow();
            Ride toRide = rideRepository.findById(request.getToRideId()).orElseThrow();
            User user = userRepository.findByUsername(auth.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!toRide.getUserId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            request.setStatus(RideStatus.CONFIRMED);
            rideMatchRequestRepository.save(request);
            return ResponseEntity.ok("Match confirmed");
        } catch (Exception e) {
            logger.error("Failed to confirm match: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/match/reject/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> rejectMatch(@PathVariable Long requestId, Authentication auth) {
        try {
            RideMatchRequest request = rideMatchRequestRepository.findById(requestId).orElseThrow();
            Ride toRide = rideRepository.findById(request.getToRideId()).orElseThrow();
            User user = userRepository.findByUsername(auth.getName());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!toRide.getUserId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            request.setStatus(RideStatus.CANCELLED);
            rideMatchRequestRepository.save(request);
            return ResponseEntity.ok("Match rejected");
        } catch (Exception e) {
            logger.error("Failed to reject match: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}