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
            RestTemplate restTemplate
    ) {
        this.rideMatchRepository = rideMatchRepository;
        this.rideRepository = rideRepository;
        this.rideService = rideService;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "ride-requests", groupId = "ride-matcher")
    @Transactional
    public void consumeRideRequest(
            @Payload String h3Index,
            @Header(KafkaHeaders.RECEIVED_KEY) String rideIdString
    ) {
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
                    ride.getPickupLon()
            ); // uses findByH3IndexInAndStatus(..., "PENDING")
            if (nearbyRides.isEmpty()) {
                logger.info("No nearby rides found for clustering for Ride ID {}", rideId);
                return;
            }

            logger.info("Found {} nearby rides for Ride ID {}", nearbyRides.size(), rideId);

            // 2) Prepare riders payload for FastAPI
List<Map<String, Object>> riders = new ArrayList<>();
for (Ride r : nearbyRides) {
    Map<String, Object> rider = new HashMap<>();

    // Route-shape feature vector from RideService
    List<Double> routeFeatures = rideService.buildRouteFeatureVector(
            r.getPickupLat(),
            r.getPickupLon(),
            r.getDropoffLat(),
            r.getDropoffLon()
    );

    rider.put("lat", r.getPickupLat());   // keep for display
    rider.put("lon", r.getPickupLon());
    rider.put("route_features", routeFeatures);
    rider.put("trustscore", rideService.getUserTrustScore(r.getUserId()));
    rider.put("rideid", r.getId());

    riders.add(rider);
}

Map<String, Object> clusterRequest = Map.of("riders", riders);

ClusterResponse[] clusters = restTemplate.postForObject(
        "http://person-detector:8000/cluster",
        clusterRequest,
        ClusterResponse[].class
);



            logger.info("Clusters received: {}", Arrays.toString(clusters));

            if (clusters == null || clusters.length == 0) {
                logger.info("No clusters returned from FastAPI for Ride ID {}", rideId);
                return;
            }

            // 4) Group rides by cluster ID
            Map<Integer, List<Long>> clusterMap = new HashMap<>();
            for (ClusterResponse c : clusters) {
                int clusterId = c.getCluster();
                Long rId = c.getRide_id();
                clusterMap
                        .computeIfAbsent(clusterId, k -> new ArrayList<>())
                        .add(rId);
            }
logger.info("Clustering request for h3Index={}, rideId={}", h3Index, rideIdString);
logger.info("Clusters response: {}", Arrays.toString(clusters));

            // 5) For EACH ride in each cluster, create/update RideMatch
// 5) For EACH ride in each cluster, create/update RideMatch with distance constraint
for (Map.Entry<Integer, List<Long>> entry : clusterMap.entrySet()) {
    int clusterId = entry.getKey();
    List<Long> rideIdsInCluster = entry.getValue()
            .stream()
            .distinct()
            .collect(Collectors.toList());

    for (Long rId : rideIdsInCluster) {
        // Load the main ride for this match
        Ride mainRide = rideRepository.findById(rId).orElse(null);
        if (mainRide == null) {
            logger.warn("Main ride not found for id {} in cluster {}", rId, clusterId);
            continue;
        }

        // All other rides in same cluster except self AND close in pickup & dropoff
        List<Long> matchedIds = rideIdsInCluster.stream()
                .filter(otherId -> !otherId.equals(rId))
                .map(otherId -> rideRepository.findById(otherId).orElse(null))
                .filter(Objects::nonNull)
                .filter(otherRide -> rideService.areRidesClose(mainRide, otherRide))
                .map(Ride::getId)
                .collect(Collectors.toList());

        // Remove old matches for this ride to avoid duplicates/stale data
        rideMatchRepository.deleteByRideId(rId);

        if (!matchedIds.isEmpty()) {
            RideMatch match = new RideMatch();
            match.setRideId(rId);
            match.setMatchedRideIds(matchedIds);
            match.setClusterId(clusterId);
            rideMatchRepository.save(match);

            logger.info(
                    "Saved cluster {} for ride {} with matches {} (after distance filter)",
                    clusterId, rId, matchedIds
            );
        } else {
            logger.info(
                    "Cluster {} for ride {} has no nearby rides after distance filter, skipping",
                    clusterId, rId
            );
        }
    }
}

            

        } catch (Exception e) {
            logger.error("Clustering failed for ride {}: {}", rideId, e.getMessage(), e);
        }
    }
}
