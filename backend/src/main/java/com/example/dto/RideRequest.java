package com.example.dto;

public class RideRequest {
    private double pickupLat;
    private double pickupLon;
    private double dropoffLat;
    private double dropoffLon;

    // Getters
    public double getPickupLat() {
        return pickupLat;
    }

    public double getPickupLon() {
        return pickupLon;
    }

    public double getDropoffLat() {
        return dropoffLat;
    }

    public double getDropoffLon() {
        return dropoffLon;
    }

    // Setters
    public void setPickupLat(double pickupLat) {
        this.pickupLat = pickupLat;
    }

    public void setPickupLon(double pickupLon) {
        this.pickupLon = pickupLon;
    }

    public void setDropoffLat(double dropoffLat) {
        this.dropoffLat = dropoffLat;
    }

    public void setDropoffLon(double dropoffLon) {
        this.dropoffLon = dropoffLon;
    }
}