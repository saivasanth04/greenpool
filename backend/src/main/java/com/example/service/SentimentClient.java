// backendsrc/main/java/com/example/service/SentimentClient.java
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

    /**
     * Returns sentiment in [-1, 1]
     */
    public double getSentimentScore(String text) {
        Map<String, String> req = Map.of("text", text);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restTemplate.postForObject(sentimentUrl, req, Map.class);
        if (resp == null || !resp.containsKey("score")) {
            throw new IllegalStateException("Sentiment service failed");
        }
        Object scoreObj = resp.get("score");
        if (!(scoreObj instanceof Number)) {
            throw new IllegalStateException("Invalid sentiment score type: " + scoreObj);
        }
        double s = ((Number) scoreObj).doubleValue();
        if (s < -1.0) s = -1.0;
        if (s > 1.0) s = 1.0;
        return s;
    }
}
