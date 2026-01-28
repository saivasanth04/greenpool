package com.example.controller;

import com.example.entity.User;
import com.example.repository.UserRepository;
import com.example.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final MinioService minioService;
    private final UserRepository userRepository;

    @GetMapping("/profiles/{fileName}")
    public ResponseEntity<byte[]> getProfile(@PathVariable String fileName, Authentication auth) throws Exception {
        User user = userRepository.findByUsername(auth.getName());
        if (user == null || !fileName.equals(user.getProfilePictureUrl())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] bytes = minioService.getObject("profiles", fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(bytes);
    }
}