import React, { useRef, useState, useEffect } from "react";
import "./CaptureModal.css";

interface Props {
  onCapture: (file: File) => void;
  onClose: () => void;
}

const CaptureModal: React.FC<Props> = ({ onCapture, onClose }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [stream, setStream] = useState<MediaStream>();

  useEffect(() => {
    navigator.mediaDevices
      .getUserMedia({ video: true })
      .then((s) => {
        setStream(s);
        if (videoRef.current) videoRef.current.srcObject = s;
      })
      .catch(() => alert("Camera permission denied"));
    return () => stream?.getTracks().forEach((t) => t.stop());
  }, []);

 const snap = () => {
  const canvas = canvasRef.current!;
  const video = videoRef.current!;
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  canvas.getContext("2d")!.drawImage(video, 0, 0);

  // ➜ release camera immediately
  stream?.getTracks().forEach(t => t.stop());

  canvas.toBlob(blob => {
    if (blob) {
      const file = new File([blob], "camera.jpg", { type: "image/jpeg" });
      onCapture(file);
    }
  }, "image/jpeg");
};

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <video ref={videoRef} autoPlay playsInline />
        <canvas ref={canvasRef} style={{ display: "none" }} />
        <div className="modal-buttons">
          <button onClick={snap}>📸 Capture</button>
          <button onClick={onClose}>❌ Cancel</button>
        </div>
      </div>
    </div>
  );
};

export default CaptureModal;