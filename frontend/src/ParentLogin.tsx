// ParentLogin.tsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "./api"; // Use shared api instance
import Header from "./components/Header";

const ParentLogin: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMsg("");
    try {
      // Send as query params for @RequestParam username/password
      await api.post("/api/parent/auth/login", null, {
        params: { username, password },
      });
      nav("/parent/home");
    } catch (err: any) {
      setMsg("Login failed: " + (err.response?.data?.message || err.message));
      console.log(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <div className="gp-container">
        <div className="gp-card" style={{ maxWidth: '400px', margin: '0 auto' }}>
          <h2 className="text-center mb-4">Parent Login</h2>
          <form onSubmit={onSubmit}>
            <input
              className="gp-input"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
            <input
              className="gp-input"
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button className="gp-btn" type="submit" disabled={loading}>
              {loading ? "Logging in..." : "Login"}
            </button>
            {msg && <p className="error-text text-center mt-2">{msg}</p>}
          </form>
          <p className="text-center mt-2">
            {/* Parent signup is restricted to internal use only */}
          </p>
        </div>
      </div>
    </>
  );
};

export default ParentLogin;
