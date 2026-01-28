package com.example.entity;

import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// new entity: Feedback.java
@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id @GeneratedValue
    private long id;

    @Column(nullable = false)
    private long fromUserId;

    @Column(nullable = false)
    private long toUserId;   // the partner being rated

    @Column(nullable = false)
    private long rideId;

    @Column(length = 2000)
    private String comment;

    @Column
    private double sentimentScore; // [-1, 1], optional to persist

    @Column
    private double weight; // V, optional

    @Column
    private Date createdAt;

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getFromUserId() { return fromUserId; }
    public void setFromUserId(long fromUserId) { this.fromUserId = fromUserId; }
    public long getToUserId() { return toUserId; }
    public void setToUserId(long toUserId) { this.toUserId = toUserId; }
    public long getRideId() { return rideId; }
    public void setRideId(long rideId) { this.rideId = rideId; }
    public String getComment() { return comment; }  
    public void setComment(String comment) { this.comment = comment; }
    public double getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.util.Date createdAt) { this.createdAt = (Date) createdAt; }


    // timestamps, getters, setters...
}

