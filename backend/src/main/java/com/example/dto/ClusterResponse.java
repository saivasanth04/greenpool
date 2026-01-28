package com.example.dto;

public class ClusterResponse {
    private Long ride_id;
    private Double lat;
    private Double lon;
    private Integer trust_score;
    private Integer cluster;

    // Getters and Setters
    public Long getRide_id() {
        return ride_id;
    }

    public void setRide_id(Long ride_id) {
        this.ride_id = ride_id;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Integer getTrust_score() {
        return trust_score;
    }

    public void setTrust_score(Integer trust_score) {
        this.trust_score = trust_score;
    }

    public Integer getCluster() {
        return cluster;
    }

    public void setCluster(Integer cluster) {
        this.cluster = cluster;
    }
}