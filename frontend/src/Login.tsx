// src/pages/Login.tsx
import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "./api";
import "./Login.css";

const Login: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const { data } = await api.post("/auth/login", { username, password });

      localStorage.setItem("avatar", data.avatar);
      localStorage.setItem("username", username);

      // More robust: full reload so cookie state is clean
      window.location.href = "/welcome";
    } catch (err: any) {
      const errorMsg =
        err.response?.data?.message ||
        err.response?.data ||
        "Invalid credentials";
      setMsg(errorMsg);
    }
  };

  return (
    <div className="login-wrapper">
      <form onSubmit={submit} className="login-box">
        <h2>Login</h2>
        <input
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {msg && <p className="msg">{msg}</p>}
        <button type="submit">Login</button>
        <p className="signup-link">
          Don’t have an account?{" "}
          <Link to="/signup" className="link">
            Sign up
          </Link>
          <h1>OR</h1>
          <Link to="/parent/login" className="link">
            Parent Login?
          </Link>
        </p>
      </form>
    </div>
  );
};

export default Login;
