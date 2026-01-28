package com.example.service;
import com.example.entity.Ride.RideStatus;

import jakarta.annotation.PostConstruct;
import com.example.dto.RideRequest;
import com.example.entity.Ride;
import com.example.entity.Ride.RideStatus;
import com.example.entity.User;
import com.example.repository.RideMatchRepository;
import com.example.repository.RideMatchRequestRepository;
import com.example.repository.RideRepository;
import com.example.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.h3core.H3Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RideService {
    private static final Logger logger = LoggerFactory.getLogger(RideService.class);
    final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final H3Core h3;
    @Autowired
private RideMatchRepository rideMatchRepository;

@Autowired
private RideMatchRequestRepository rideMatchRequestRepository;
    private static final double EARTH_RADIUS = 6371; // km
    private static final double EMISSION_FACTOR = 0.2; // kg CO2/km
    
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public RideService(RideRepository rideRepository, UserRepository userRepository, KafkaTemplate<String, String> kafkaTemplate, RestTemplate restTemplate, RedisTemplate<String, Object> redisTemplate) throws IOException {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        try {
            this.h3 = H3Core.newInstance();
            logger.info("Successfully initialized H3Core");
        } catch (IOException e) {
            logger.error("Failed to initialize H3Core: {}", e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Ride createRide(RideRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new IllegalStateException("Authenticated user not found");
        }

        if (!isValidLat(request.getPickupLat()) || !isValidLon(request.getPickupLon()) ||
            !isValidLat(request.getDropoffLat()) || !isValidLon(request.getDropoffLon())) {
            throw new IllegalArgumentException("Invalid coordinates");
        }

        double distance = calculateDistance(
            request.getPickupLat(), request.getPickupLon(),
            request.getDropoffLat(), request.getDropoffLon()
        );

        Map<String, String> pickupAddr = reverseGeocode(request.getPickupLat(), request.getPickupLon());
        Map<String, String> dropoffAddr = reverseGeocode(request.getDropoffLat(), request.getDropoffLon());

        Ride ride = new Ride();
        ride.setPickupLat(request.getPickupLat());
        ride.setPickupLon(request.getPickupLon());
        ride.setDropoffLat(request.getDropoffLat());
        ride.setDropoffLon(request.getDropoffLon());
        ride.setPickupAddress(pickupAddr.getOrDefault("display_name", "Unknown"));
        ride.setDropoffAddress(dropoffAddr.getOrDefault("display_name", "Unknown"));
        ride.setUserId(user.getId());
        long h3IndexLong = h3.latLngToCell(request.getPickupLat(), request.getPickupLon(), 8);
        String h3Index = h3.h3ToString(h3IndexLong);
        ride.setH3Index(h3Index);
        ride.setCarbonEstimate((distance * EMISSION_FACTOR)/2);

        Ride savedRide = rideRepository.save(ride);
        logger.info("Ride created: ID={}, User={}, H3={}", savedRide.getId(), user.getId(), h3Index);

        try {
            kafkaTemplate.send("ride-requests", savedRide.getId().toString(), h3Index);
            logger.info("Kafka message sent for rideId: {}, h3: {}", savedRide.getId(), h3Index);
        } catch (Exception e) {
            logger.error("Failed to publish Kafka event for ride {}: {}", savedRide.getId(), e.getMessage());
        }

        return savedRide;
    }

    public List<Ride> getNearbyRides(double pickupLat, double pickupLon) {
        long centerLong = h3.latLngToCell(pickupLat, pickupLon, 8);
        String center = h3.h3ToString(centerLong);
        List<String> ring = h3.gridDisk(center, 2); // ~5km
        return rideRepository.findByH3IndexInAndStatus(ring, RideStatus.PENDING);
    }

    public Integer getUserTrustScore(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return user.getTrustScore();
    }
    
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
public void cleanupOldRides() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(1);
    
    // Delete pending rides older than 1 day
    List<Ride> oldPending = rideRepository.findByStatusAndCreatedAtBefore(
        Ride.RideStatus.PENDING, threshold);
    oldPending.forEach(ride -> {
        // Remove from clusters if any
        rideMatchRepository.deleteByRideId(ride.getId());
        rideRepository.delete(ride);
    });

    // Delete completed rides older than 1 day
    List<Ride> oldCompleted = rideRepository.findByStatusAndCreatedAtBefore(
        Ride.RideStatus.COMPLETED, threshold);
    oldCompleted.forEach(ride -> {
        rideRepository.delete(ride);
        // Optionally also clean related match requests
        rideMatchRequestRepository.deleteByFromRideIdOrToRideId(ride.getId(), ride.getId());
    });
}
// In RideService.java (or a new ScheduledTasks class)




private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
    // CHANGE: Use OSRM API for road distance instead of Haversine
    try {
        String url = "http://router.project-osrm.org/route/v1/driving/" + lon1 + "," + lat1 + ";" + lon2 + "," + lat2 + "?overview=false";
        String response = restTemplate.getForObject(url, String.class);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        if (root.has("routes") && !root.get("routes").isEmpty()) {
            return root.get("routes").get(0).get("distance").asDouble() / 1000.0; // km
        }
    } catch (Exception e) {
        logger.warn("OSRM API failed, fallback to Haversine: {}", e.getMessage());
    }
    // Fallback to Haversine
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
               Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS * c;
}

    private boolean isValidLat(double lat) {
        return lat >= -90 && lat <= 90;
    }

    private boolean isValidLon(double lon) {
        return lon >= -180 && lon <= 180;
    }

    public Map<String, Double> geocode(String address) {
        String cacheKey = "geocode:" + address.toLowerCase().replace(" ", "_");
        @SuppressWarnings("unchecked")
        Map<String, Double> cached = (Map<String, Double>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            logger.info("Geocode cache hit for: {}", address);
            return cached;
        }

        try {
            String url = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&format=json&limit=1";
            ResponseEntity<List<Map<String, String>>> response = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, String>>>() {});
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                Map<String, String> result = response.getBody().get(0);
                Map<String, Double> location = Map.of(
                    "lat", Double.parseDouble(result.get("lat")),
                    "lon", Double.parseDouble(result.get("lon"))
                );
                redisTemplate.opsForValue().set(cacheKey, location, Duration.ofDays(7));
                logger.info("Geocoded address: {} -> {}", address, location);
                return location;
            }
            logger.warn("No geocode results for: {}", address);
            return null;
        } catch (Exception e) {
            logger.error("Geocoding failed for {}: {}", address, e.getMessage());
            return null;
        }
    }

    public Map<String, String> reverseGeocode(double lat, double lon) {
        String cacheKey = String.format("reverse-geocode:%.6f,%.6f", lat, lon);
        @SuppressWarnings("unchecked")
        Map<String, String> cached = (Map<String, String>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            logger.info("Reverse geocode cache hit for: {},{}", lat, lon);
            return cached;
        }

        try {
            String url = String.format("https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json", lat, lon);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                String displayName = result.containsKey("display_name") ? (String) result.get("display_name") : "";
                Map<String, String> location = new HashMap<>();
                location.put("display_name", displayName);
                redisTemplate.opsForValue().set(cacheKey, location, Duration.ofDays(7));
                logger.info("Reverse geocoded: {},{} -> {}", lat, lon, displayName);
                return location;
            }
            logger.warn("No reverse geocode results for: {},{}", lat, lon);
            return Map.of("display_name", "Unknown");
        } catch (Exception e) {
            logger.error("Reverse geocoding failed for {},{}: {}", lat, lon, e.getMessage());
            return Map.of("display_name", "Unknown");
        }
    }
}