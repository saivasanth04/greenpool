// frontendsrc/pages/FeedbackPage.tsx
import React, { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

const APIBASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

const FeedbackPage: React.FC = () => {
  const { rideId } = useParams<{ rideId: string }>();
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rideId) {
      toast.error("Invalid ride ID");
      return;
    }
    if (!comment.trim()) {
      toast.error("Please enter your feedback");
      return;
    }

    try {
      setSubmitting(true);
      await axios.post(
        `${APIBASE}/api/feedback/${rideId}`,
        { comment },
        { withCredentials: true }
      );
      toast.success("Thank you for your feedback!");
      setTimeout(() => {
        navigate("/"); // go back to home
      }, 10000);
    } catch (err: any) {
      console.error("Feedback submit error", err);
      toast.error(
        err?.response?.data || "Failed to submit feedback. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: "20px", maxWidth: "600px", margin: "0 auto" }}>
      <ToastContainer position="top-right" autoClose={3000} />
      <h2>Ride Feedback</h2>
      <p>Please share your experience for ride #{rideId}.</p>
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "12px" }}>
          <label htmlFor="comment" style={{ display: "block", marginBottom: 4 }}>
            Feedback
          </label>
          <textarea
            id="comment"
            rows={5}
            style={{ width: "100%", padding: 8 }}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="How was your ride and your partner?"
          />
        </div>
        <button
          type="submit"
          disabled={submitting}
          style={{
            padding: "8px 16px",
            background: "#007bff",
            color: "#fff",
            border: "none",
            borderRadius: 4,
            cursor: submitting ? "default" : "pointer",
          }}
        >
          {submitting ? "Submitting..." : "Submit Feedback"}
        </button>
      </form>
    </div>
  );
};

export default FeedbackPage;
