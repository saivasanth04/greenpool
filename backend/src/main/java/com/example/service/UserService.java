package com.example.service;

import com.example.config.JwtTokenProvider;
import com.example.dto.SignupRequest;
import com.example.entity.User;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MinioService minioService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${person.detector.url:http://localhost:8000/detect_person}")
    private String personDetectorUrl;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       MinioService minioService,
                       RestTemplate restTemplate,
                       RedisTemplate<String, Object> redisTemplate,
                       KafkaTemplate<String, String> kafkaTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.minioService = minioService;
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    private static class UserEvent {
        private String type;
        private String username;
        private Integer trustScore;

        public UserEvent(String type, String username, Integer trustScore) {
            this.type = type;
            this.username = username;
            this.trustScore = trustScore;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getTrustScore() { return trustScore; }
        public void setTrustScore(Integer trustScore) { this.trustScore = trustScore; }
    }

    @Transactional
    public boolean signup(SignupRequest request) {
        try {
            if (userRepository.findByUsername(request.getUsername()) != null) {
                logger.info("Signup attempted but username already exists: {}", request.getUsername());
                return false;
            }

            byte[] imageBytes = request.getProfilePicture().getBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override public String getFilename() { return "profile.jpg"; }
            });
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Boolean> response;
            try {
                response = restTemplate.postForObject(personDetectorUrl, entity, Map.class);
            } catch (RestClientException rce) {
                logger.warn("Person detector unavailable, allowing signup: {}", rce.getMessage());
                response = Map.of("has_person", true);
            }

            if (response == null || !response.getOrDefault("has_person", false)) {
                logger.info("No person detected for user: {}", request.getUsername());
                return false;
            }

            String fileName = System.currentTimeMillis() + "_" + request.getUsername() + ".jpg";
            String objectName = minioService.uploadImage(fileName, imageBytes);

            User user = new User();
            user.setUsername(request.getUsername());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setProfilePictureUrl(objectName);
            user.setTrustScore(100);
            user.setPhoneNumber(request.getPhoneNumber());
            userRepository.save(user);

            redisTemplate.opsForValue().set("user:" + user.getUsername() + ":trustScore", 100, Duration.ofHours(1));
            try {
                kafkaTemplate.send("user-events", objectMapper.writeValueAsString(new UserEvent("SIGNUP", user.getUsername(), null)));
                logger.info("Published SIGNUP event for {}", user.getUsername());
            } catch (Exception e) {
                logger.error("Failed to publish SIGNUP event: {}", e.getMessage());
            }

            logger.info("User registered: {}", request.getUsername());
            return true;
        } catch (Exception e) {
            logger.error("Signup failed for user {}: {}", request.getUsername(), e.getMessage(), e);
            return false;
        }
    }

    public String signupAndReturnObjectName(String username, String password, MultipartFile file,String phoneNumber) {
        SignupRequest req = new SignupRequest(username, password, file,phoneNumber);
        boolean ok = signup(req);
        if (!ok) return null;
        User u = userRepository.findByUsername(username);
        return u != null ? u.getProfilePictureUrl() : null;
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword())) {
            return jwtTokenProvider.generateToken(username, user.getTrustScore());
        }
        return null;
    }

    @Transactional
    public void updateTrustScore(Long userId, int delta) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        int newScore = Math.max(0, Math.min(100, user.getTrustScore() + delta));
        user.setTrustScore(newScore);
        userRepository.save(user);
        try {
            redisTemplate.opsForValue().set("user:" + user.getUsername() + ":trustScore", newScore, Duration.ofHours(1));
            logger.info("Cached trust score for {}: {}", user.getUsername(), newScore);
        } catch (Exception e) {
            logger.error("Failed to cache trust score for {}: {}", user.getUsername(), e.getMessage());
        }
        try {
            kafkaTemplate.send("user-events", objectMapper.writeValueAsString(new UserEvent("TRUST_SCORE_UPDATE", user.getUsername(), newScore)));
            logger.info("Published TRUST_SCORE_UPDATE event for {}: {}", user.getUsername(), newScore);
        } catch (Exception e) {
            logger.error("Failed to publish TRUST_SCORE_UPDATE event: {}", e.getMessage());
        }
        logger.info("Trust score updated for {} (id={}) -> {}", user.getUsername(), userId, newScore);
    }

    public int getTrustScore(String username) {
        Object cachedScore = redisTemplate.opsForValue().get("user:" + username + ":trustScore");
        if (cachedScore instanceof Integer) return (Integer) cachedScore;
        User user = userRepository.findByUsername(username);
        if (user == null) throw new IllegalArgumentException("User not found");
        redisTemplate.opsForValue().set("user:" + username + ":trustScore", user.getTrustScore(), Duration.ofHours(1));
        logger.info("Cached trust score for {}: {}", username, user.getTrustScore());
        return user.getTrustScore();
    }
}