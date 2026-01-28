// backend/src/main/java/com/example/entity/Ride.java (Added pickupAddress and dropoffAddress)
package com.example.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "rides", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_h3index", columnList = "h3_index"),
    @Index(name = "idx_user_id", columnList = "user_id")
})
public class Ride {
    public enum RideStatus {
    PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED
}
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Min(-90)
    @Max(90)
    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @NotNull
    @Min(-180)
    @Max(180)
    @Column(name = "pickup_lon", nullable = false)
    private double pickupLon;

    @NotNull
    @Min(-90)
    @Max(90)
    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @CreationTimestamp
@Column(updatable = false)
private LocalDateTime createdAt;

    @NotNull
    @Min(-180)
    @Max(180)
    @Column(name = "dropoff_lon", nullable = false)
    private double dropoffLon;

    @Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false)
private RideStatus status = RideStatus.PENDING;

    @Column(name = "carbon_estimate")
    private double carbonEstimate;

    @NotBlank
    @Column(name = "h3_index", nullable = false)
    private String h3Index;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;  // Link to User entity (add @ManyToOne if full relation)

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "dropoff_address")
    private String dropoffAddress;
    @Column(name = "current_lat")
    private Double currentLat = 0.0;

    @Column(name = "current_lon")
    private Double currentLon = 0.0;
    // Getters and Setters
    // In Ride entity (add if missing)


public LocalDateTime getCreatedAt() { return createdAt; }
public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public double getPickupLat() { return pickupLat; }
    public void setPickupLat(double pickupLat) { this.pickupLat = pickupLat; }
    public double getPickupLon() { return pickupLon; }
    public void setPickupLon(double pickupLon) { this.pickupLon = pickupLon; }
    public double getDropoffLat() { return dropoffLat; }
    public void setDropoffLat(double dropoffLat) { this.dropoffLat = dropoffLat; }
    public double getDropoffLon() { return dropoffLon; }
    public void setDropoffLon(double dropoffLon) { this.dropoffLon = dropoffLon; }
    public RideStatus getStatus() { return status; }
public void setStatus(RideStatus status) { this.status = status; }
    public double getCarbonEstimate() { return carbonEstimate; }
    public void setCarbonEstimate(double carbonEstimate) { this.carbonEstimate = carbonEstimate; }
    public String getH3Index() { return h3Index; }
    public void setH3Index(String h3Index) { this.h3Index = h3Index; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; }
    // CHANGE: New getters/setters
    public Double getCurrentLat() {
        return currentLat;
    }

    public void setCurrentLat(Double currentLat) {
        this.currentLat = currentLat;
    }

    public Double getCurrentLon() {
        return currentLon;
    }

    public void setCurrentLon(Double currentLon) {
        this.currentLon = currentLon;
    }
}