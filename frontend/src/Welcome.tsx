// src/pages/Welcome.tsx
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "./api";
import "./Welcome.css";

const MINIO_BASE = "http://localhost:9000/profiles";

const Welcome: React.FC = () => {
  const nav = useNavigate();
  const [trustScore, setTrustScore] = useState<string>("NA");
  const [phoneNumber, setPhoneNumber] = useState<string>("");
  const [error, setError] = useState<string>("");
  const username = localStorage.getItem("username") || "User";
  const avatarObject = localStorage.getItem("avatar");

  useEffect(() => {
    const load = async () => {
      try {
        const { data } = await api.get("/auth/me");
        if (data.error) {
          setError(data.error);
          setTrustScore("NA");
          return;
        }
        setTrustScore(data.trustScore?.toString() ?? "NA");
        setPhoneNumber(data.phoneNumber || "");
        setError("");
      } catch (err: any) {
        const msg =
          err.response?.data?.error ||
          err.response?.data ||
          "Failed to fetch user data";
        setError(msg);
        setTrustScore("NA");
      }
    };
    load();
  }, []);

  const avatarUrl = avatarObject
    ? `${MINIO_BASE}/${avatarObject}`
    : undefined;

  return (
    <div className="welcome-wrap">
      <div className="welcome-card">
        <div className="avatar-circle">
          {avatarUrl ? <img src={avatarUrl} alt="avatar" /> : <span>A</span>}
        </div>
        <h3>Welcome {username},</h3>
        {error && <p style={{ color: "red" }}>{error}</p>}
        <p>
          Your trust score <strong>{trustScore}</strong>
        </p>
        {phoneNumber && (
          <p>
            Phone <strong>{phoneNumber}</strong>
          </p>
        )}
        <p>Please continue your journey</p>
        <button onClick={() => nav("/home")}>Go to Home</button>
      </div>
    </div>
  );
};

export default Welcome;
