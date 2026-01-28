// ParentLogin.tsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "./api";

const ParentLogin: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [msg, setMsg] = useState("");

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      // Send as query params for @RequestParam username/password
      await api.post("/parent/auth/login", null, {
        params: { username, password },
      });
      nav("/parent/home");
    } catch (err: any) {
      setMsg(err+"Login failed");
      console.log(err);
    }
  };

  return (
    <form onSubmit={onSubmit}>
      <input
        placeholder="Username"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
      />
      <input
        type="password"
        placeholder="Password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <button type="submit">Login</button>
      {msg && <p>{msg}</p>}
    </form>
  );
};

export default ParentLogin;
