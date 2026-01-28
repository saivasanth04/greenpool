package com.example.dto;

import org.springframework.web.multipart.MultipartFile;

public class SignupRequest {
  private String username;
  private String password;
  private MultipartFile profilePicture;
  private String phoneNumber;



  public SignupRequest(String username, String password, MultipartFile profilePicture, String phoneNumber) {
    this.username = username;
    this.password = password;
    this.profilePicture = profilePicture;
    this.phoneNumber = phoneNumber;
   
  
  }

  // Getters
  public String getUsername() { return username; }
  public String getPassword() { return password; }
  public MultipartFile getProfilePicture() { return profilePicture; }
  public String getPhoneNumber() { return phoneNumber; }

  
  
}