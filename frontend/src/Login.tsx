// src/pages/Login.tsx
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "./api";
import Header from "./components/Header";
// import "./Login.css"; // Removed custom CSS in favor of global styles

const Login: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const { data } = await api.post("/api/auth/login", { username, password });

      localStorage.setItem("avatar", data.avatar);
      localStorage.setItem("username", username);

      // More robust: full reload so cookie state is clean
      nav("/welcome");
    } catch (err: any) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data ||
        "Invalid credentials";
      setMsg(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <div className="gp-container">
        <div className="gp-card" style={{ maxWidth: '400px', margin: '0 auto' }}>
          <h2 className="text-center mb-4">Login</h2>
          <form onSubmit={submit}>
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

            <div className="text-center mt-4">
              <p className="mb-2">
                Don’t have an account?{" "}
                <Link to="/signup">Sign up</Link>
              </p>
              <div style={{ margin: '10px 0', borderTop: '1px solid #eee' }}></div>
              <p>
                <Link to="/parent/login" style={{ color: 'var(--secondary-color)' }}>
                  Parent Login
                </Link>
              </p>
            </div>
          </form>
        </div>
      </div>
    </>
  );
};

export default Login;
