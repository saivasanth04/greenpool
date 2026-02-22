import React, { useEffect, useState } from "react";
import axios from "axios";
import { MapContainer, TileLayer, Marker } from "react-leaflet";
import 'leaflet/dist/leaflet.css'; // ADDED: Import Leaflet CSS

import { api } from "./api";

const ParentHome: React.FC = () => {
  const [data, setData] = useState({ childLat: 0, childLon: 0, rideStatus: "IDLE", partnerUsername: "", partnerPhone: "", childRideId: 0 });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await api.get("/api/parent/auth/me");
        setData(res.data);
      } catch (error) {
        console.error("Failed to fetch parent data:", error);
      }
    };

    fetchData(); // ADDED: Initial fetch on mount

    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  // ADDED: Conditionally render map only if valid lat/lon (not 0)
  const hasValidPosition = data.childLat !== 0 && data.childLon !== 0;

  return (
    <div>
      <h1>Child Status</h1>
      <p>Ride Status: {data.rideStatus}</p>
      <p>Partner: {data.partnerUsername} ({data.partnerPhone})</p>
      {hasValidPosition ? (
        <MapContainer
          center={[data.childLat, data.childLon]}
          zoom={13}
          style={{ height: '400px', width: '100%' }} // ADDED: Set height/width
        >
          <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
          <Marker position={[data.childLat, data.childLon]} />
        </MapContainer>
      ) : (
        <p>Loading map...</p> // Placeholder while waiting for data
      )}
    </div>
  );
};

export default ParentHome;