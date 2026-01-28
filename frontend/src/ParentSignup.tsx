// frontend/src/pages/ParentSignup.tsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "./api";

const ParentSignup: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const fd = new FormData();
    fd.append("username", username);
    fd.append("password", password);

    try {
      await api.post("/parent/auth/signup", fd);
      setMsg("Signup successful");
      setTimeout(() => nav("/parent/login"), 1500);
    } catch (err: any) {
      setMsg(err.response?.data || "Signup failed");
    }
  };

  return (
    <form onSubmit={onSubmit}>
      <input
        placeholder="Parent Username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
      />
      <input
        type="password"
        placeholder="Parent Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      {/* No child field anymore – backend uses logged-in child */}
      <button type="submit">Signup</button>
      {msg && <p>{msg}</p>}
    </form>
  );
};

export default ParentSignup;
