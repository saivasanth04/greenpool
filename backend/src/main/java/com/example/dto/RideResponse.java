// backend/src/main/java/com/example/dto/RideResponse.java
package com.example.dto;

public class RideResponse {
    private Long id;
    private double pickupLat;
    private double pickupLon;
    private double dropoffLat;
    private double dropoffLon;
    private String status;
    private double carbonEstimate;
    private String h3Index;
    private Long userId;
    private String pickupAddress;
    private String dropoffAddress;

    public RideResponse(Long id, double pickupLat, double pickupLon, double dropoffLat, double dropoffLon,
                        String status, double carbonEstimate, String h3Index, Long userId,
                        String pickupAddress, String dropoffAddress) {
        this.id = id;
        this.pickupLat = pickupLat;
        this.pickupLon = pickupLon;
        this.dropoffLat = dropoffLat;
        this.dropoffLon = dropoffLon;
        this.status = status;
        this.carbonEstimate = carbonEstimate;
        this.h3Index = h3Index;
        this.userId = userId;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
    }

    // --- ALL Getters are required for JSON serialization ---
    public Long getId() { return id; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLon() { return pickupLon; }
    public double getDropoffLat() { return dropoffLat; }
    public double getDropoffLon() { return dropoffLon; }
    public String getStatus() { return status; }
    public double getCarbonEstimate() { return carbonEstimate; }
    public String getH3Index() { return h3Index; }
    public Long getUserId() { return userId; }
    public String getPickupAddress() { return pickupAddress; }
    public String getDropoffAddress() { return dropoffAddress; }
}