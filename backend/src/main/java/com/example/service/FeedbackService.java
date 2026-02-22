// backendsrc/main/java/com/example/service/FeedbackService.java
package com.example.service;

import com.example.entity.Feedback;
import com.example.entity.Ride;
import com.example.entity.RideMatchRequest;
import com.example.entity.User;
import com.example.repository.FeedbackRepository;
import com.example.repository.RideMatchRequestRepository;
import com.example.repository.RideRepository;
import com.example.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final RideMatchRequestRepository rideMatchRequestRepository;
    private final SentimentClient sentimentClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            RideRepository rideRepository,
            RideMatchRequestRepository rideMatchRequestRepository,
            SentimentClient sentimentClient,
            RedisTemplate<String, Object> redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.rideRepository = rideRepository;
        this.rideMatchRequestRepository = rideMatchRequestRepository;
        this.sentimentClient = sentimentClient;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * fromUser gives feedback about partner for given ride.
     * We infer partner from RideMatchRequest with COMPLETED status.
     */
    @Transactional
    public void submitFeedback(Long fromUserId, Long rideId, String comment) {
        logger.info("=== FEEDBACK SUBMISSION START === fromUserId={}, rideId={}, comment length={}",
                fromUserId, rideId, comment != null ? comment.length() : 0);

        try {
            // 1. Verify from user exists
            User fromUser = userRepository.findById(fromUserId)
                    .orElseThrow(() -> {
                        logger.error("FAIL: From user not found. fromUserId={}", fromUserId);
                        return new IllegalArgumentException("From user not found");
                    });
            logger.info("Step 1 PASS: From user found. username={}", fromUser.getUsername());

            // 2. Verify ride exists
            Ride ride = rideRepository.findById(rideId)
                    .orElseThrow(() -> {
                        logger.error("FAIL: Ride not found. rideId={}", rideId);
                        return new IllegalArgumentException("Ride not found");
                    });
            logger.info("Step 2 PASS: Ride found. userId={}, status={}", ride.getUserId(), ride.getStatus());

            // 3. Verify ownership
            if (!ride.getUserId().equals(fromUserId)) {
                logger.error("FAIL: Ownership check failed. rideUserId={}, fromUserId={}",
                        ride.getUserId(), fromUserId);
                throw new IllegalArgumentException("User does not own this ride");
            }
            logger.info("Step 3 PASS: Ownership verified");

            // 4. Resolve partner
            Long toUserId = resolvePartnerUserId(rideId);
            if (toUserId == null) {
                logger.warn(
                        "FAIL: No partner found (no COMPLETED match for rideId={}). Returning without storing feedback.",
                        rideId);
                return; // keep your current behavior
            }
            logger.info("Step 4 PASS: Partner resolved. toUserId={}", toUserId);

            // 5. Get partner user
            User toUser = userRepository.findById(toUserId)
                    .orElseThrow(() -> {
                        logger.error("FAIL: Partner user not found. toUserId={}", toUserId);
                        return new IllegalArgumentException("Partner user not found");
                    });
            logger.info("Step 5 PASS: Partner user found. username={}", toUser.getUsername());

            // 6. Calculate sentiment (NOW: score + pPositive + confidence)
            // Requires SentimentClient.analyze(String) as discussed.
            SentimentClient.SentimentResult sr = sentimentClient.analyze(comment);

            double sentiment = sr.getScore(); // signed [-1, 1]
            double pPositive = sr.getPPositive(); // [0, 1]
            double confidence = sr.getConfidence(); // [0, 1]
            String label = sr.getLabel();

            logger.info("Step 6 PASS: Sentiment calculated. score={}, pPositive={}, confidence={}, label={}",
                    sentiment, pPositive, confidence, label);

            // FIX: If label is explicit POSITIVE, force high probability to prevent score
            // drop for "good" feedback
            if ("POSITIVE".equalsIgnoreCase(label)) {
                pPositive = Math.max(pPositive, 0.99);
            }

            // 7. Update trust (Bayesian Beta update, confidence-weighted)
            double alpha = toUser.getTrustAlpha();
            double beta = toUser.getTrustBeta();
            logger.info("Step 7a: Trust state before update. alpha={}, beta={}", alpha, beta);

            // Weight per feedback = confidence (optionally keep a minimum floor so weak
            // predictions still count slightly)
            double V = confidence;
            if (V < 0.2)
                V = 0.2; // optional safety floor; remove if you want pure confidence

            // Evidence increments MUST be non-negative
            double rInc = V * pPositive; // positive evidence
            double sInc = V * (1.0 - pPositive); // negative evidence

            alpha += rInc;
            beta += sInc;

            // Keep > 0 to avoid divide-by-zero
            if (alpha < 0.1)
                alpha = 0.1;
            if (beta < 0.1)
                beta = 0.1;

            toUser.setTrustAlpha(alpha);
            toUser.setTrustBeta(beta);

            // Use mean instead of mode (mean is always stable for alpha,beta > 0)
            double p = alpha / (alpha + beta);
            if (p < 0)
                p = 0;
            if (p > 1)
                p = 1;

            int oldScore = toUser.getTrustScore();
            int rawScore = (int) Math.round(100.0 * p);

            // Keep your existing bounded change behavior (prevents jumps)
            int maxDeltaPerFeedback = 5;
            int bounded = oldScore + Math.max(
                    -maxDeltaPerFeedback,
                    Math.min(maxDeltaPerFeedback, rawScore - oldScore));

            int finalScore = Math.max(0, Math.min(100, bounded));

            // CORRECTION: Positive feedback should NEVER lower the score
            if ("POSITIVE".equalsIgnoreCase(label) && finalScore < oldScore) {
                logger.info("Correction: Positive feedback lowered score from {} to {}. Resetting to {}.", oldScore,
                        finalScore, oldScore);
                finalScore = oldScore;
            }

            toUser.setTrustScore(finalScore);
            userRepository.save(toUser);

            logger.info(
                    "Step 7b: Trust state UPDATED. oldScore={}, newScore={}, rawScore={}, alpha={}, beta={}, V={}, rInc={}, sInc={}",
                    oldScore, finalScore, rawScore, alpha, beta, V, rInc, sInc);

            // 8. Store feedback in DB (store both signed sentiment and weight used)
            Feedback fb = new Feedback();
            fb.setFromUserId(fromUserId);
            fb.setToUserId(toUserId);
            fb.setRideId(rideId);
            fb.setComment(comment);

            // Keep signed score for debugging/analytics
            fb.setSentimentScore(sentiment);

            // Store actual weight used in Bayesian update (confidence-weighted)
            fb.setWeight(V);

            fb.setCreatedAt(new Date(System.currentTimeMillis()));

            feedbackRepository.save(fb);
            logger.info("Step 8 PASS: Feedback persisted. feedbackId={}", fb.getId());

            // 9. Publish to Kafka
            try {
                String payload = objectMapper.writeValueAsString(
                        Map.of(
                                "type", "FEEDBACK",
                                "fromUserId", fromUserId,
                                "toUserId", toUserId,
                                "rideId", rideId,

                                // keep old field name for compatibility
                                "sentiment", sentiment,

                                // add extra fields (safe; consumers can ignore)
                                "pPositive", pPositive,
                                "confidence", confidence,
                                "label", label,
                                "weightUsed", V,

                                "newTrustScore", finalScore));
                kafkaTemplate.send("user-events", payload);
                logger.info("Step 9 PASS: Feedback event published to Kafka");
            } catch (Exception e) {
                logger.error("Step 9 WARN: Kafka publish failed (non-fatal)", e);
            }

            // 10. Cache and notify (optional)
            try {
                cacheAndPublishTrustScore(toUser.getUsername(), finalScore);
                logger.info("Step 10 PASS: Trust score cached and published");
            } catch (Exception e) {
                logger.error("Step 10 WARN: Cache/publish failed (non-fatal)", e);
            }

            logger.info("=== FEEDBACK SUBMISSION SUCCESS ===");

        } catch (Exception e) {
            logger.error("=== FEEDBACK SUBMISSION FAILED ===", e);
            throw e;
        }
    }

    private Long resolvePartnerUserId(Long rideId) {
        logger.info("resolvePartnerUserId START: rideId={}", rideId);

        List<Long> ids = List.of(rideId);
        List<RideMatchRequest> reqs = rideMatchRequestRepository
                .findByFromRideIdInAndStatusOrToRideIdInAndStatus(
                        ids, Ride.RideStatus.COMPLETED,
                        ids, Ride.RideStatus.COMPLETED);

        logger.info("resolvePartnerUserId: Found {} COMPLETED matches for rideId={}", reqs.size(), rideId);

        if (reqs.isEmpty()) {
            logger.warn("resolvePartnerUserId: No COMPLETED match found. Returns null");
            return null;
        }

        RideMatchRequest req = reqs.get(0);
        Long partnerRideId = rideId.equals(req.getFromRideId()) ? req.getToRideId() : req.getFromRideId();
        logger.info("resolvePartnerUserId: partnerRideId={}", partnerRideId);

        Ride partnerRide = rideRepository.findById(partnerRideId).orElse(null);
        if (partnerRide == null) {
            logger.error("resolvePartnerUserId: Partner ride not found. partnerRideId={}", partnerRideId);
            return null;
        }

        Long partnerUserId = partnerRide.getUserId();
        logger.info("resolvePartnerUserId: Partner found. partnerUserId={}", partnerUserId);
        return partnerUserId;
    }

    private void cacheAndPublishTrustScore(String username, int newScore) {
        try {
            redisTemplate.opsForValue().set(
                    "user:" + username + ":trustScore",
                    newScore,
                    Duration.ofHours(1));
        } catch (Exception e) {
            logger.error("Failed to cache trust score for {}: {}", username, e.getMessage());
        }

        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of(
                            "type", "TRUSTSCOREUPDATE",
                            "username", username,
                            "trustScore", newScore));
            kafkaTemplate.send("user-events", payload);
        } catch (Exception e) {
            logger.error("Failed to publish TRUSTSCOREUPDATE event: {}", e.getMessage());
        }
    }
}
