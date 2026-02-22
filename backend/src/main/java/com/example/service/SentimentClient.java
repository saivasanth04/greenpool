package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class SentimentClient {
    private final RestTemplate restTemplate;

    @Value("${sentiment.url:http://person-detector:8000/sentiment}")
    private String sentimentUrl;

    public SentimentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // NEW: structured result for Bayesian update
    public static class SentimentResult {
        private final double score;       // [-1, 1]
        private final double pPositive;   // [0, 1]
        private final double confidence;  // [0, 1]
        private final String label;

        public SentimentResult(double score, double pPositive, double confidence, String label) {
            this.score = score;
            this.pPositive = pPositive;
            this.confidence = confidence;
            this.label = label;
        }

        public double getScore() { return score; }
        public double getPPositive() { return pPositive; }
        public double getConfidence() { return confidence; }
        public String getLabel() { return label; }
    }

    public SentimentResult analyze(String text) {
        Map<String, String> req = Map.of("text", text);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restTemplate.postForObject(sentimentUrl, req, Map.class);

        if (resp == null || !resp.containsKey("score")) {
            throw new IllegalStateException("Sentiment service failed");
        }

        double score = asDouble(resp.get("score"));
        // Keep safe bounds
        if (score < -1.0) score = -1.0;
        if (score > 1.0) score = 1.0;

        // New fields (fallbacks keep system working even if Python not updated yet)
        double pPositive = resp.containsKey("pPositive") ? asDouble(resp.get("pPositive")) : (score + 1.0) / 2.0;
        double confidence = resp.containsKey("confidence") ? asDouble(resp.get("confidence")) : 1.0;
        String label = resp.containsKey("label") ? String.valueOf(resp.get("label")) : (score >= 0 ? "POSITIVE" : "NEGATIVE");

        // Clamp
        pPositive = clamp01(pPositive);
        confidence = clamp01(confidence);

        return new SentimentResult(score, pPositive, confidence, label);
    }

    // Keep old method so other classes won’t conflict
    public double getSentimentScore(String text) {
        return analyze(text).getScore();
    }

    private static double asDouble(Object o) {
        if (!(o instanceof Number)) throw new IllegalStateException("Invalid numeric value: " + o);
        return ((Number) o).doubleValue();
    }

    private static double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }
}
