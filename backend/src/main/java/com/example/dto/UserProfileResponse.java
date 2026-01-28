// New: backend/src/main/java/com/example/dto/UserProfileResponse.java
package com.example.dto;

public class UserProfileResponse {
    private Long id;
    private String username;
    private int trustScore;
    private String profilePictureUrl;
     private String phoneNumber;
    private Long parentId;

    public UserProfileResponse(Long id, String username, int trustScore, String profilePictureUrl,String phoneNumber,Long parentId) {
        this.id = id;
        this.username = username;
        this.trustScore = trustScore;
        this.profilePictureUrl = profilePictureUrl;
        this.phoneNumber=phoneNumber;
        this.parentId=parentId;
    }

    public Long getId() {
        return id;
    }
    public Long parentId(){
        return parentId;
    }
    public String phoneNumber(){
        return phoneNumber;
    }

    public String getUsername() {
        return username;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }
}