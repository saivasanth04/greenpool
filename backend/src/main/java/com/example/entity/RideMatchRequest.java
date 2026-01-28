// backend/src/main/java/com/example/entity/RideMatchRequest.java (New entity for match requests)
package com.example.entity;

import com.example.entity.Ride.RideStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "ride_match_requests")
public class RideMatchRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_ride_id", nullable = false)
    private Long fromRideId;

    @Column(name = "to_ride_id", nullable = false)
    private Long toRideId;

    @Column(name = "status", nullable = false)
    private RideStatus status; // PENDING, CONFIRMED, REJECTED
    @Column(name = "start_confirmed_from", nullable = false)
    private boolean startConfirmedFrom = false;

    @Column(name = "start_confirmed_to", nullable = false)
    private boolean startConfirmedTo = false;

    @Column(name = "end_confirmed_from", nullable = false)
    private boolean endConfirmedFrom = false;

    @Column(name = "end_confirmed_to", nullable = false)
    private boolean endConfirmedTo = false;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromRideId() { return fromRideId; }
    public void setFromRideId(Long fromRideId) { this.fromRideId = fromRideId; }
    public Long getToRideId() { return toRideId; }
    public void setToRideId(Long toRideId) { this.toRideId = toRideId; }
    public RideStatus getStatus() { return status; }
    public void setStatus(Ride.RideStatus pending) { this.status = pending; }
    public boolean isStartConfirmedFrom() { return startConfirmedFrom; }
    public void setStartConfirmedFrom(boolean startConfirmedFrom) { this.startConfirmedFrom = startConfirmedFrom; }
    public boolean isStartConfirmedTo() { return startConfirmedTo; }
    public void setStartConfirmedTo(boolean startConfirmedTo) { this.startConfirmedTo = startConfirmedTo; }
    public boolean isEndConfirmedFrom() { return endConfirmedFrom; }
    public void setEndConfirmedFrom(boolean endConfirmedFrom) { this.endConfirmedFrom = endConfirmedFrom; }
    public boolean isEndConfirmedTo() { return endConfirmedTo; }
    public void setEndConfirmedTo(boolean endConfirmedTo) { this.endConfirmedTo = endConfirmedTo; }
}