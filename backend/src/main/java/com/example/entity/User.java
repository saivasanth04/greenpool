package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class User {
  @Id
  @GeneratedValue
  private Long id;
  private String username;
  private String password;
  private String profilePictureUrl;
  private int trustScore = 99;
  @Column(name = "phone_number")
  private String phoneNumber;

  // CHANGE: Added for parent-child linking
  @Column(name = "parent_id")
  private Long parentId;
  // in User.java
  @Column(name = "trust_alpha")
  private double trustAlpha = 49.5; // example prior: N0=50, p0=0.99

  @Column(name = "trust_beta")
  private double trustBeta = 0.5;

  // Getters and Setters
  public double getTrustAlpha() {
    return trustAlpha;
  }

  public double getTrustBeta() {
    return trustBeta;
  }

  public void setTrustAlpha(double trustAlpha) {
    this.trustAlpha = trustAlpha;
  }

  public void setTrustBeta(double trustBeta) {
    this.trustBeta = trustBeta;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getProfilePictureUrl() {
    return profilePictureUrl;
  }

  public void setProfilePictureUrl(String profilePictureUrl) {
    this.profilePictureUrl = profilePictureUrl;
  }

  public int getTrustScore() {
    return trustScore;
  }

  public void setTrustScore(int trustScore) {
    this.trustScore = trustScore;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }
}