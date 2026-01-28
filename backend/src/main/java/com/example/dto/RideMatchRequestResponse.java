// backend/src/main/java/com/example/dto/RideMatchRequestResponse.java (New DTO)
package com.example.dto;

import com.example.entity.Ride.RideStatus;

public class RideMatchRequestResponse {
    private Long id;
    private Long fromRideId;
    private Long toRideId;
    private RideStatus status;

    public RideMatchRequestResponse(Long id, Long fromRideId, Long toRideId, RideStatus status) {
        this.id = id;
        this.fromRideId = fromRideId;
        this.toRideId = toRideId;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getFromRideId() { return fromRideId; }
    public Long getToRideId() { return toRideId; }
    public RideStatus getStatus() { return status; }
}