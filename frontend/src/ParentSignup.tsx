// frontend/src/pages/ParentSignup.tsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "./api"; // Shared api
import Header from "./components/Header";

const ParentSignup: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    const fd = new FormData();
    fd.append("username", username);
    fd.append("password", password);

    try {
      await api.post("/api/parent/auth/signup", fd);
      setMsg("Signup successful");
      setTimeout(() => nav("/parent/login"), 1500);
    } catch (err: any) {
      setMsg(err.response?.data || "Signup failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <div className="gp-container">
        <div className="gp-card" style={{ maxWidth: '400px', margin: '0 auto' }}>
          <h2 className="text-center mb-4">Parent Signup</h2>
          <form onSubmit={onSubmit}>
            <input
              className="gp-input"
              placeholder="Parent Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
            <input
              className="gp-input"
              type="password"
              placeholder="Parent Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            {/* No child field anymore – backend uses logged-in child */}
            <button className="gp-btn" type="submit" disabled={loading}>
              {loading ? "Signing up..." : "Signup"}
            </button>
            {msg && <p className={`text-center mt-2 ${msg.includes("successful") ? "text-green-600" : "error-text"}`}>{msg}</p>}
          </form>
          <p className="text-center mt-2">
            Already have an account? <a href="/parent/login">Login</a>
          </p>
        </div>
      </div>
    </>
  );
};

export default ParentSignup;
