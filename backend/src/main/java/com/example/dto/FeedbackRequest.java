// backendsrc/main/java/com/example/dto/FeedbackRequest.java
package com.example.dto;

public class FeedbackRequest {
    private String comment;

    public FeedbackRequest() {}

    public FeedbackRequest(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
