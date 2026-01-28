// backend/src/main/java/com/example/entity/RideMatch.java
package com.example.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "ride_matches")
public class RideMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ride_id", nullable = false)
    private Long rideId;

    @ElementCollection
    @CollectionTable(name = "matched_ride_ids", joinColumns = @JoinColumn(name = "ride_match_id"))
    private List<Long> matchedRideIds;

    @Column(name = "cluster_id")
    private int clusterId;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }
    public List<Long> getMatchedRideIds() { return matchedRideIds; }
    public void setMatchedRideIds(List<Long> matchedRideIds) { this.matchedRideIds = matchedRideIds; }
    public int getClusterId() { return clusterId; }
    public void setClusterId(int clusterId) { this.clusterId = clusterId; }
}