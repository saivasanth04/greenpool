// src/pages/Signup.tsx
import React, { useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "./api";
import CaptureModal from "./CaptureModal";
import Header from "./components/Header";
// import "./Signup.css"; // Using global styles

const Signup: React.FC = () => {
  const nav = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState("");
  const [msg, setMsg] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [openCamera, setOpenCamera] = useState(false);
  const [loading, setLoading] = useState(false);
  const fileRef = useRef<HTMLInputElement | null>(null);

  const handleFile = (f: File) => {
    setFile(f);
    setPreview(URL.createObjectURL(f));
    setMsg("");
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setMsg("Please select an image");
      return;
    }
    setLoading(true);

    const fd = new FormData();
    fd.append("username", username);
    fd.append("password", password);
    fd.append("profilePicture", file);
    fd.append("phoneNumber", phoneNumber);

    try {
      const { data } = await api.post("/api/auth/signup", fd, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      setMsg(data.message || "Signup successful");

      localStorage.setItem("avatar", data.avatar);
      setTimeout(() => nav("/login"), 1500);
    } catch (err: any) {
      const errorMsg =
        err.response?.data?.message || err.response?.data || "Signup failed";
      setMsg(errorMsg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <div className="gp-container">
        {openCamera && (
          <CaptureModal
            onCapture={(f) => {
              setOpenCamera(false);
              handleFile(f);
            }}
            onClose={() => setOpenCamera(false)}
          />
        )}

        <div className="gp-card" style={{ maxWidth: '500px', margin: '0 auto' }}>
          <h2 className="text-center mb-4">Create Account</h2>

          <form onSubmit={onSubmit}>
            <input
              className="gp-input"
              placeholder="Phone number"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
            />
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

            <div className="text-center mb-4">
              <div
                style={{
                  width: '100px',
                  height: '100px',
                  borderRadius: '50%',
                  border: '2px solid #ddd',
                  margin: '0 auto 1rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  overflow: 'hidden'
                }}
              >
                {preview ? (
                  <img src={preview} alt="preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : (
                  <span style={{ color: '#aaa' }}>No image</span>
                )}
              </div>

              <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
                <button
                  type="button"
                  className="gp-btn gp-btn-secondary"
                  style={{ width: 'auto' }}
                  onClick={() => fileRef.current?.click()}
                >
                  Upload File
                </button>
                <button
                  type="button"
                  className="gp-btn gp-btn-secondary"
                  style={{ width: 'auto' }}
                  onClick={() => setOpenCamera(true)}
                >
                  Use Camera
                </button>
              </div>
            </div>

            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              hidden
              onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
            />

            <button type="submit" className="gp-btn" disabled={loading}>
              {loading ? "Creating..." : "Signup"}
            </button>

            {msg && (
              <p className={`text-center mt-2 ${msg.includes("successful") ? "text-green-600" : "error-text"}`}>
                {msg}
              </p>
            )}
          </form>
        </div>
      </div>
    </>
  );
};

export default Signup;
