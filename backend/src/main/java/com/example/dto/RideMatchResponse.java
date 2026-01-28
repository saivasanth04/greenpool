// backend/src/main/java/com/example/dto/RideMatchResponse.java
package com.example.dto;

import java.util.List;

public class RideMatchResponse {
    private Long rideId;
    private List<Long> matchedRideIds;
    private int clusterId;

    public RideMatchResponse(Long rideId, List<Long> matchedRideIds, int clusterId) {
        this.rideId = rideId;
        this.matchedRideIds = matchedRideIds;
        this.clusterId = clusterId;
    }

    // Getters
    public Long getRideId() { return rideId; }
    public List<Long> getMatchedRideIds() { return matchedRideIds; }
    public int getClusterId() { return clusterId; }
}