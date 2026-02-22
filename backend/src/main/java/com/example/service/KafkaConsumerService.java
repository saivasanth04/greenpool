package com.example.service;

import com.example.dto.ClusterResponse;
import com.example.entity.Ride;
import com.example.entity.RideMatch;
import com.example.repository.RideMatchRepository;
import com.example.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.example.entity.Ride.RideStatus;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final RideMatchRepository rideMatchRepository;
    private final RideRepository rideRepository;
    private final RideService rideService;
    private final RestTemplate restTemplate;

    @Value("${cluster.url:http://person-detector:8000/cluster}")
    private String clusterUrl;

    public KafkaConsumerService(
            RideMatchRepository rideMatchRepository,
            RideRepository rideRepository,
            RideService rideService,
            RestTemplate restTemplate) {
        this.rideMatchRepository = rideMatchRepository;
        this.rideRepository = rideRepository;
        this.rideService = rideService;
        this.restTemplate = restTemplate;
    }

@KafkaListener(topics = "ride-requests", groupId = "ride-matcher")
@Transactional
public void consumeRideRequest(
        @Payload String h3Index,
        @Header(KafkaHeaders.RECEIVED_KEY) String rideIdString) {
    logger.info("Received Kafka Message. Key (RideID) = {}, Payload (H3) = {}", rideIdString, h3Index);

    Long rideId;
    try {
        rideId = Long.parseLong(rideIdString);
    } catch (NumberFormatException e) {
        logger.error("Failed to parse Ride ID from Kafka key: {}", rideIdString);
        return;
    }

    logger.info("Consuming ride request for ID {}", rideId);

    try {
        Ride ride = rideRepository.findById(rideId).orElse(null);
        if (ride == null) {
            logger.warn("Ride not found for ID {}", rideId);
            return;
        }

        // 1) Fetch nearby rides (PENDING in same H3 ring)
        List<Ride> nearbyRides = rideService.getNearbyRides(
                ride.getPickupLat(),
                ride.getPickupLon());
        if (nearbyRides.isEmpty()) {
            logger.info("No nearby rides found for clustering for Ride ID {}", rideId);
            return;
        }

        logger.info("Found {} nearby rides for Ride ID {}", nearbyRides.size(), rideId);

        // 2) Prepare riders payload for FastAPI (include current ride last)
        List<Map<String, Object>> riders = new ArrayList<>();
        for (Ride r : nearbyRides) {
            Map<String, Object> rider = new HashMap<>();

            List<Double> routeFeatures = r.getRouteFeatures();
            if (routeFeatures == null || routeFeatures.isEmpty()) {
                routeFeatures = rideService.buildRouteFeatureVector(
                        r.getPickupLat(), r.getPickupLon(),
                        r.getDropoffLat(), r.getDropoffLon());
            }

            rider.put("lat", r.getPickupLat());
            rider.put("lon", r.getPickupLon());
            rider.put("route_features", routeFeatures);
            rider.put("trustscore", rideService.getUserTrustScore(r.getUserId()));
            rider.put("rideid", r.getId());

            riders.add(rider);
        }

        // IMPORTANT: Append the CURRENT ride LAST so Python can prioritize it
        Map<String, Object> currentRider = new HashMap<>();
        List<Double> currentFeatures = ride.getRouteFeatures();
        if (currentFeatures == null || currentFeatures.isEmpty()) {
            currentFeatures = rideService.buildRouteFeatureVector(
                    ride.getPickupLat(), ride.getPickupLon(),
                    ride.getDropoffLat(), ride.getDropoffLon());
        }
        currentRider.put("lat", ride.getPickupLat());
        currentRider.put("lon", ride.getPickupLon());
        currentRider.put("route_features", currentFeatures);
        currentRider.put("trustscore", rideService.getUserTrustScore(ride.getUserId()));
        currentRider.put("rideid", ride.getId());
        riders.add(currentRider);  // ← appended last

        Map<String, Object> clusterRequest = Map.of("riders", riders);

        // Call FastAPI
        ClusterResponse[] clusters = restTemplate.postForObject(
                clusterUrl,
                clusterRequest,
                ClusterResponse[].class);

        if (clusters == null || clusters.length == 0) {
            logger.info("No clusters returned from FastAPI for Ride ID {}", rideId);
            return;
        }

        logger.info("Clusters received ({} total): {}", clusters.length, Arrays.toString(clusters));

        // ── NEW: Prioritize matches for the CURRENT ride ────────────────────────
        // Find the cluster info for our ride (search instead of assuming last)
        ClusterResponse currentCluster = null;
        for (ClusterResponse c : clusters) {
            if (c.getRide_id().equals(rideId)) {
                currentCluster = c;
                break;
            }
        }

        if (currentCluster == null) {
            logger.warn("No cluster info returned for current ride {}", rideId);
            return;
        }

        Integer currentClusterId = currentCluster.getCluster();
        logger.info("Current ride {} assigned to cluster {}", rideId, currentClusterId);

        // Find all rides that got the same cluster ID
        List<Long> sameClusterRideIds = Arrays.stream(clusters)
                .filter(c -> c.getCluster().equals(currentClusterId))
                .map(ClusterResponse::getRide_id)
                .filter(id -> !id.equals(rideId))  // exclude self
                .distinct()
                .collect(Collectors.toList());

        logger.info("Found {} candidate matches in cluster {} for ride {}", 
                    sameClusterRideIds.size(), currentClusterId, rideId);

        // Apply your existing close filter
        List<Long> filteredMatches = sameClusterRideIds.stream()
                .map(id -> rideRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(otherRide -> {
                    boolean close = rideService.areRidesClose(ride, otherRide);
                    if (!close) {
                        logger.debug("Filtered out ride {} - not close enough to {}", otherRide.getId(), rideId);
                    }
                    return close;
                })
                .map(Ride::getId)
                .collect(Collectors.toList());

        // Remove old matches for this ride
        rideMatchRepository.deleteByRideId(rideId);

        if (!filteredMatches.isEmpty()) {
            RideMatch match = new RideMatch();
            match.setRideId(rideId);
            match.setMatchedRideIds(filteredMatches);
            match.setClusterId(currentClusterId);
            rideMatchRepository.save(match);

            logger.info("Saved {} matches for ride {} in cluster {}: {}", 
                        filteredMatches.size(), rideId, currentClusterId, filteredMatches);
        } else {
            logger.info("No matches survived areRidesClose filter for ride {} in cluster {}", 
                        rideId, currentClusterId);
        }

        // Optional: still process other clusters if you want (but not necessary for this flow)
        // You can keep or remove the original loop below — it's not hurting anything

    } catch (Exception e) {
        logger.error("Clustering failed for ride {}: {}", rideId, e.getMessage(), e);
    }
}
}
