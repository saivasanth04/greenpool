// File: C:\Projects\EchoU\backend\src\main\java\com\example\entity\Parent.java (new file)

package com.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "parents")
public class Parent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column
    private String username;
    @Column
    private String password;

    @Column
    private Long childId;

    @Column
    private double childLat = 0.0;

    @Column
    private double childLon = 0.0;

    @Column
    private String rideStatus = "IDLE";

    @Column
    private String partnerUsername;

    @Column
    private String partnerPhone;

    @Column
    private Long childRideId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public double getChildLat() { return childLat; }
    public void setChildLat(double childLat) { this.childLat = childLat; }
    public double getChildLon() { return childLon; }
    public void setChildLon(double childLon) { this.childLon = childLon; }
    public String getRideStatus() { return rideStatus; }
    public void setRideStatus(String rideStatus) { this.rideStatus = rideStatus; }
    public String getPartnerUsername() { return partnerUsername; }
    public void setPartnerUsername(String partnerUsername) { this.partnerUsername = partnerUsername; }
    public String getPartnerPhone() { return partnerPhone; }
    public void setPartnerPhone(String partnerPhone) { this.partnerPhone = partnerPhone; }
    public Long getChildRideId() { return childRideId; }
    public void setChildRideId(Long childRideId) { this.childRideId = childRideId; }
}