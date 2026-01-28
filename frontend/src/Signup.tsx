// src/pages/Signup.tsx
import React, { useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import api from "./api";
import CaptureModal from "./CaptureModal";
import "./Signup.css";

const Signup: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [msg, setMsg] = useState("");
  const [color, setColor] = useState<string>();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [openCamera, setOpenCamera] = useState(false);
  const fileRef = useRef<HTMLInputElement | null>(null);

  const handleFile = (f: File) => {
    setFile(f);
    setPreview(URL.createObjectURL(f));
    setMsg("");
    setColor(undefined);
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setMsg("Please select an image");
      return;
    }

    const fd = new FormData();
    fd.append("username", username);
    fd.append("password", password);
    fd.append("profilePicture", file);
    fd.append("phoneNumber", phoneNumber);

    try {
      const { data } = await api.post("/auth/signup", fd, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      setColor("green");
      setMsg(data.message || "Signup successful");

      localStorage.setItem("avatar", data.avatar);
      setTimeout(() => nav("/login"), 1500);
    } catch (err: any) {
      setColor("red");
      const errorMsg =
        err.response?.data?.message || err.response?.data || "Signup failed";
      setMsg(errorMsg);
    }
  };

  return (
    <div className="signup-wrapper">
      {openCamera && (
        <CaptureModal
          onCapture={(f) => {
            setOpenCamera(false);
            handleFile(f);
          }}
          onClose={() => setOpenCamera(false)}
        />
      )}

      <form onSubmit={onSubmit} className="signup-box">
        <h2>Create account</h2>

        <input
          placeholder="Phone number"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
        />
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

        <div className="avatar-circle" style={{ borderColor: color || "#ccc" }}>
          {preview ? <img src={preview} alt="preview" /> : <span>No image</span>}
        </div>

        <div className="img-buttons">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
          >
            File
          </button>
          <button
            type="button"
            onClick={() => setOpenCamera(true)}
          >
            Camera
          </button>
        </div>

        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
        />

        {msg && (
          <p className="msg" style={{ color: color || "inherit" }}>
            {msg}
          </p>
        )}

        <button type="submit" className="submit-btn">
          Signup
        </button>
      </form>
    </div>
  );
};

export default Signup;
