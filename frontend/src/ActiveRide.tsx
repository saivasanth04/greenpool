import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { toast, ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import L from "leaflet";
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  Polyline,
  useMap,
} from "react-leaflet";
import "leaflet/dist/leaflet.css";

import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

const API_BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

interface Ride {
  id: number;
  dropoffLat: number;
  dropoffLon: number;
  dropoffAddress: string;
  status: string;
  pickupLat: number;
  pickupLon: number;
  pickupAddress: string;
}

interface Match {
  id: number;
  fromRideId: number;
  toRideId: number;
  status: string;
}

const userIcon = new L.Icon({
  iconUrl: "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
const dropoffIcon = new L.Icon({
  iconUrl: "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
const carIcon = new L.Icon({
  iconUrl: "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-orange.png",
  iconSize: [30, 48],
  iconAnchor: [15, 48],
});

const formatDistance = (meters: number | null) => {
  if (meters === null) return "--";
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(2)} km`;
};

function MapUpdater({ position }: { position: [number, number] | null }) {
  const map = useMap();
  useEffect(() => {
    if (position) {
      map.setView(position, map.getZoom(), { animate: true });
    }
  }, [position, map]);
  return null;
}

const ActiveRide: React.FC = () => {
  const navigate = useNavigate();
  const [ride, setRide] = useState<Ride | null>(null);
  const [matchId, setMatchId] = useState<number | null>(null);
  const [userPos, setUserPos] = useState<[number, number] | null>(null);
  const [carPos, setCarPos] = useState<[number, number] | null>(null);
  const [routeCoords, setRouteCoords] = useState<L.LatLng[]>([]);
  const [loading, setLoading] = useState(true);
  const [journeyStarted, setJourneyStarted] = useState(false);
  const [distanceMeters, setDistanceMeters] = useState<number | null>(null);
  const [waitingForPartner, setWaitingForPartner] = useState(false);
  const [rideChecked, setRideChecked] = useState(false);

  // CRITICAL FIX: Check active ride only once on mount, don't redirect immediately
  useEffect(() => {
    const checkActiveRide = async () => {
      try {
        const r = await axios.get<Ride | null>(`${API_BASE}/api/rides/active`, { 
          withCredentials: true,
          timeout: 5000
        });
        
        // If no active ride and no completed ride needing feedback
        if (!r.data) {
          console.log("No active ride found");
          setRideChecked(true);
          setLoading(false);
          // Only navigate if we're sure there's no ride
          // Don't navigate immediately - let user see "no active ride" message
          return;
        }

        // If ride is COMPLETED, go to feedback immediately
        if (r.data.status === "COMPLETED") {
          console.log("Ride completed, redirecting to feedback");
          navigate(`/ride/${r.data.id}/feedback`);
          return;
        }

        setRide(r.data);
        setRideChecked(true);
        
        // Check for confirmed match
        try {
          const m = await axios.get<Match[]>(`${API_BASE}/api/rides/matches/confirmed`, { 
            withCredentials: true,
            timeout: 5000
          });
          const match = m.data.find(x => x.fromRideId === r.data?.id || x.toRideId === r.data?.id);
          setMatchId(match?.id ?? null);
        } catch (err) {
          console.error("Error fetching matches:", err);
        }
        
        setLoading(false);
      } catch (err: any) {
        console.error("Error checking active ride:", err);
        if (err.response?.status === 401 || err.response?.status === 403) {
          toast.error("Session expired. Please login again.");
          navigate("/login");
        } else {
          setRideChecked(true);
          setLoading(false);
        }
      }
    };

    checkActiveRide();
  }, [navigate]);

  // Poll for ride status changes (for when partner ends ride)
  useEffect(() => {
    if (!ride?.id || ride.status === "COMPLETED") return;

    const pollInterval = setInterval(async () => {
      try {
        const res = await axios.get<Ride>(`${API_BASE}/api/rides/${ride.id}`, { 
          withCredentials: true,
          timeout: 3000
        });
        
        // If ride became completed, redirect to feedback
        if (res.data.status === "COMPLETED") {
          toast.success("Ride completed by partner!");
          navigate(`/ride/${ride.id}/feedback`);
        }
      } catch (err) {
        // Silent fail on polling errors
        console.log("Polling error:", err);
      }
    }, 5000); // Poll every 5 seconds

    return () => clearInterval(pollInterval);
  }, [ride?.id, ride?.status, navigate]);

  const startJourney = async () => {
    if (!matchId) return toast.error("No match found");
    try {
      await axios.post(`${API_BASE}/api/rides/match/start/${matchId}`, {}, { 
        withCredentials: true,
        timeout: 10000
      });
      toast.success("Journey started!");
      setJourneyStarted(true);
    } catch (err: any) {
      console.error("Start journey error:", err);
      toast.error(err.response?.data?.message || "Failed to start journey");
    }
  };

  // GPS tracking only after journey starts
  useEffect(() => {
    if (!journeyStarted || !navigator.geolocation) return;

    const watchId = navigator.geolocation.watchPosition(
      pos => {
        const p: [number, number] = [pos.coords.latitude, pos.coords.longitude];
        setUserPos(p);
        setCarPos(p);
      },
      err => console.warn("GPS error", err),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 2000 }
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [journeyStarted]);

  // Route calculation
  useEffect(() => {
    if (!ride || !userPos) return;

    const fetchRoute = async () => {
      const url = `https://router.project-osrm.org/route/v1/driving/${userPos[1]},${userPos[0]};${ride.dropoffLon},${ride.dropoffLat}?overview=full&geometries=geojson`;
      try {
        const res = await axios.get(url, { timeout: 5000 });
        const coords = res.data.routes[0].geometry.coordinates.map(
          ([lng, lat]: [number, number]) => L.latLng(lat, lng)
        );
        setRouteCoords(coords);
        setDistanceMeters(res.data.routes[0].distance);
      } catch {
        setRouteCoords([
          L.latLng(userPos[0], userPos[1]),
          L.latLng(ride.dropoffLat, ride.dropoffLon),
        ]);
        const from = L.latLng(userPos[0], userPos[1]);
        const to = L.latLng(ride.dropoffLat, ride.dropoffLon);
        setDistanceMeters(from.distanceTo(to));
      }
    };
    fetchRoute();
  }, [ride, userPos]);

  // Location updates to backend
  useEffect(() => {
    if (!userPos || !ride?.id || !journeyStarted) return;

    const interval = setInterval(async () => {
      try {
        await axios.post(`${API_BASE}/api/rides/${ride.id}/location`, null, { 
          params: { lat: userPos[0], lon: userPos[1] }, 
          withCredentials: true,
          timeout: 5000
        });
      } catch (err) {
        console.error("Location update error:", err);
      }
    }, 20000);
    
    return () => clearInterval(interval);
  }, [userPos, ride?.id, journeyStarted]);

  const endJourney = async () => {
    if (!matchId) return toast.error("No match found");

    try {
      const res = await axios.post(
        `${API_BASE}/api/rides/match/end/${matchId}`,
        {},
        { 
          withCredentials: true,
          timeout: 10000
        }
      );

      const data = res.data as { 
        message?: string; 
        rideId?: number; 
        completedForBoth?: boolean;
        redirectToFeedback?: boolean;
      };

      if (data.completedForBoth || data.redirectToFeedback) {
        toast.success(data.message || "Ride completed!");
        if (data.rideId) {
          // Small delay to ensure backend state is committed
          setTimeout(() => {
            navigate(`/ride/${data.rideId}/feedback`);
          }, 500);
        }
      } else {
        setWaitingForPartner(true);
        toast.info(data.message || "Waiting for partner to end ride...");
      }
    } catch (err: any) {
      console.error("End journey error:", err);
      if (err.response?.status === 401 || err.response?.status === 403) {
        toast.error("Session expired. Please login again.");
        navigate("/login");
      } else {
        toast.error(err.response?.data?.message || "Failed to end ride");
      }
    }
  };

  // Show "No Active Ride" state
  if (rideChecked && !ride && !loading) {
    return (
      <div style={{ 
        height: "100vh", 
        display: "flex", 
        flexDirection: "column",
        alignItems: "center", 
        justifyContent: "center",
        gap: "20px"
      }}>
        <h2>No Active Ride</h2>
        <p>You don't have any active rides at the moment.</p>
        <button 
          onClick={() => navigate("/home")}
          style={{
            padding: "12px 24px",
            background: "#007bff",
            color: "#fff",
            border: "none",
            borderRadius: "8px",
            cursor: "pointer"
          }}
        >
          Go to Home
        </button>
      </div>
    );
  }

  if (loading || !rideChecked) {
    return (
      <div style={{ height: "100vh", display: "grid", placeItems: "center" }}>
        Loading ride…
      </div>
    );
  }

  return (
    <div style={{ height: "100vh", width: "100vw", position: "relative" }}>
      <MapContainer
        center={userPos || (ride ? [ride.dropoffLat, ride.dropoffLon] : [17.385, 78.4867])}
        zoom={16}
        style={{ height: "100%", width: "100%" }}
      >
        <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />

        {userPos && (
          <Marker position={userPos} icon={userIcon}>
            <Popup>You are here</Popup>
          </Marker>
        )}

        {ride && (
          <Marker position={[ride.dropoffLat, ride.dropoffLon]} icon={dropoffIcon}>
            <Popup>Drop-off: {ride.dropoffAddress}</Popup>
          </Marker>
        )}

        {carPos && (
          <Marker position={carPos} icon={carIcon}>
            <Popup>Your ride</Popup>
          </Marker>
        )}

        {routeCoords.length > 1 && (
          <Polyline positions={routeCoords} color="#3388ff" weight={6} opacity={0.7} />
        )}

        <MapUpdater position={userPos} />
      </MapContainer>

      <div
        style={{
          position: "absolute",
          bottom: 30,
          left: "50%",
          transform: "translateX(-50%)",
          zIndex: 1000,
          display: "flex",
          flexDirection: "column",
          gap: 12,
          alignItems: "center",
        }}
      >
        <div style={{
          background: "rgba(59, 15, 190, 0.12)",
          padding: "8px 16px",
          borderRadius: 20,
          boxShadow: "0 2px 8px rgba(0,0,0,0.12)",
          fontWeight: 600,
        }}>
          Distance : {formatDistance(distanceMeters)}
        </div>

        {!journeyStarted ? (
          <button onClick={startJourney} style={{
            padding: "16px 40px",
            background: "#4caf50",
            color: "#fff",
            border: "none",
            borderRadius: 30,
            fontSize: 18,
            fontWeight: "bold",
            cursor: "pointer",
          }}>
            Start Journey
          </button>
        ) : (
          <button
            onClick={endJourney}
            disabled={waitingForPartner}
            style={{
              padding: "16px 40px",
              background: waitingForPartner ? "#9e9e9e" : "#d32f2f",
              color: "#fff",
              border: "none",
              borderRadius: 30,
              fontSize: 18,
              fontWeight: "bold",
              cursor: waitingForPartner ? "default" : "pointer",
            }}>
            {waitingForPartner ? "Waiting for Partner..." : "End Journey"}
          </button>
        )}
      </div>

      <ToastContainer position="top-center" />
    </div>
  );
};

export default ActiveRide;