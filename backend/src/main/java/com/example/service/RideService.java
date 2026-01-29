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
    private static final String GEOAPIFY_API_KEY = "c7be5432e67d49ba9055bffad38ed9eb";
    private static final double EARTH_RADIUS = 6371; // km
    private static final double EMISSION_FACTOR = 0.2; // kg CO2/km
    // How many points to sample per route for clustering
private static final int ROUTE_SAMPLES = 20; // you can tune (20–40 is fine)

// Tolerances in km for final pickup/dropoff checks
private static final double MAX_PICKUP_DIST_KM = 2.0;  // 2 km
private static final double MAX_DROPOFF_DIST_KM = 2.0; // 2 km

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
/**
 * Fetch full OSRM route geometry as list of [lat, lon] points.
 */
private List<double[]> fetchRoutePoints(double pickupLat,
                                        double pickupLon,
                                        double dropoffLat,
                                        double dropoffLon) {
    try {
        // OSRM expects lon,lat;lon,lat
        String coords = String.format(
                Locale.US,
                "%f,%f;%f,%f",
                pickupLon, pickupLat,
                dropoffLon, dropoffLat
        );

        String url = "http://router.project-osrm.org/route/v1/driving/" + coords
                + "?overview=full&geometries=geojson&steps=false&alternatives=false";

        String response = restTemplate.getForObject(url, String.class);
        if (response == null) {
            logger.warn("OSRM route response null for coords={}", coords);
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        JsonNode routes = root.get("routes");
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            logger.warn("No routes in OSRM response for coords={}", coords);
            return List.of();
        }

        JsonNode geometry = routes.get(0).get("geometry");
        if (geometry == null || geometry.isNull()) {
            logger.warn("No geometry in OSRM route for coords={}", coords);
            return List.of();
        }

        JsonNode coordsNode = geometry.get("coordinates");
        if (coordsNode == null || !coordsNode.isArray() || coordsNode.isEmpty()) {
            logger.warn("No coordinates in OSRM geometry for coords={}", coords);
            return List.of();
        }

        List<double[]> points = new ArrayList<>();
        for (JsonNode c : coordsNode) {
            if (c.size() < 2) continue;
            double lon = c.get(0).asDouble();
            double lat = c.get(1).asDouble();
            points.add(new double[]{lat, lon}); // store as [lat, lon]
        }
        logger.info("Fetched {} route points from OSRM for coords={}", points.size(), coords);
        return points;
    } catch (Exception e) {
        logger.warn("OSRM route fetch failed: {}", e.getMessage());
        return List.of();
    }
}

/**
 * Resample a polyline (list of [lat,lon]) to exactly n points by index interpolation.
 * This is simple and works well enough for your use case.
 */
private List<double[]> resampleRoute(List<double[]> points, int n) {
    List<double[]> result = new ArrayList<>(n);

    if (points == null || points.isEmpty()) {
        return result;
    }
    if (points.size() == 1) {
        double[] p = points.get(0);
        for (int i = 0; i < n; i++) {
            result.add(new double[]{p[0], p[1]});
        }
        return result;
    }

    int lastIdx = points.size() - 1;
    for (int i = 0; i < n; i++) {
        double t = i * (double) lastIdx / Math.max(1, n - 1);
        int j0 = (int) Math.floor(t);
        int j1 = (int) Math.ceil(t);
        if (j0 == j1) {
            double[] p = points.get(j0);
            result.add(new double[]{p[0], p[1]});
        } else {
            double w = t - j0;
            double[] p0 = points.get(j0);
            double[] p1 = points.get(j1);
            double lat = p0[0] + w * (p1[0] - p0[0]);
            double lon = p0[1] + w * (p1[1] - p0[1]);
            result.add(new double[]{lat, lon});
        }
    }
    logger.info("Resampled route to {} points", result);
    return result;
}

/**
 * Build a fixed-length route-shape feature vector for clustering:
 * [lat1, lon1, lat2, lon2, ..., latN, lonN].
 * Falls back to straight line if OSRM fails.
 */
public List<Double> buildRouteFeatureVector(double pickupLat,
                                            double pickupLon,
                                            double dropoffLat,
                                            double dropoffLon) {
    List<double[]> points = fetchRoutePoints(pickupLat, pickupLon, dropoffLat, dropoffLon);

    // Fallback to straight line if OSRM failed
    if (points.isEmpty()) {
        points = List.of(
                new double[]{pickupLat, pickupLon},
                new double[]{dropoffLat, dropoffLon}
        );
    }

    List<double[]> sampled = resampleRoute(points, ROUTE_SAMPLES);
    if (sampled.isEmpty()) {
        sampled = resampleRoute(
                List.of(
                        new double[]{pickupLat, pickupLon},
                        new double[]{dropoffLat, dropoffLon}
                ),
                ROUTE_SAMPLES
        );
    }

    List<Double> features = new ArrayList<>(2 * ROUTE_SAMPLES);
    for (double[] p : sampled) {
        features.add(p[0]); // lat
        features.add(p[1]); // lon
    }
    logger.info("Built route feature vector with {} points", features);
    return features;
}

/**
 * Simple helper to enforce "nearby pickup & dropoff" when using clusters.
 */
public boolean areRidesClose(Ride a, Ride b) {
    double pickupDistKm = calculateDistance(
            a.getPickupLat(), a.getPickupLon(),
            b.getPickupLat(), b.getPickupLon()
    );
    double dropoffDistKm = calculateDistance(
            a.getDropoffLat(), a.getDropoffLon(),
            b.getDropoffLat(), b.getDropoffLon()
    );

    return pickupDistKm <= MAX_PICKUP_DIST_KM && dropoffDistKm <= MAX_DROPOFF_DIST_KM;
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
            logger.info("OSRM route found between ({}, {}) and ({}, {})", lat1, lon1, lat2, lon2);
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
    logger.info("Haversine distance calculated between ({}, {}) and ({}, {})", lat1, lon1, lat2, lon2);
    return EARTH_RADIUS * c;
}

    private boolean isValidLat(double lat) {
        return lat >= -90 && lat <= 90;
    }

    private boolean isValidLon(double lon) {
        return lon >= -180 && lon <= 180;
    }

public Map<String, Double> geocode(String address) {
        String url = "https://api.geoapify.com/v1/geocode/search?text="
                + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&filter=countrycode:in&limit=1&apiKey=" + GEOAPIFY_API_KEY;

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map body = response.getBody();
        if (body == null) {
            logger.warn("Empty response body from Geoapify for address: {}", address);
            return null;
        }

        List features = (List) body.get("features");
        if (features == null || features.isEmpty()) {
            logger.warn("No features returned from Geoapify for address: {}", address);
            return null;
        }

        Map first = (Map) features.get(0);
        Map geometry = (Map) first.get("geometry");
        List coords = (List) geometry.get("coordinates");

        double lon = ((Number) coords.get(0)).doubleValue();
        double lat = ((Number) coords.get(1)).doubleValue();

        logger.info("Geocoded address: {} -> lat: {}, lon: {}", address, lat, lon);

        return Map.of("lat", lat, "lon", lon);
   
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