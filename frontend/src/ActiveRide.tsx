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
  useMap, // ADDED: For dynamic map updates
} from "react-leaflet";
import "leaflet/dist/leaflet.css";

// Fix default Leaflet icons
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
}
interface Match {
  id: number;
  fromRideId: number;
  toRideId: number;
  status: string;
}

// Icons
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

// ADDED: Component to dynamically update map view when position changes
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

  // New: distance in meters (null = unknown)
  const [distanceMeters, setDistanceMeters] = useState<number | null>(null);

  // 1. Load ride & match
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const r = await axios.get<Ride>(`${API_BASE}/api/rides/active`, { withCredentials: true });
        if (!r.data) return navigate("/home");

        const m = await axios.get<Match[]>(`${API_BASE}/api/rides/matches/confirmed`, { withCredentials: true });
        const match = m.data.find(x => x.fromRideId === r.data.id || x.toRideId === r.data.id);

        if (!cancelled) {
          setRide(r.data);
          setMatchId(match?.id ?? null);
          setLoading(false);
        }
      } catch {
        if (!cancelled) navigate("/home");
      }
    };
    load();
    return () => { cancelled = true; };
  }, [navigate]);

  // 2. Start journey
  const startJourney = async () => {
    if (!matchId) return toast.error("No match");
    try {
      await axios.post(`${API_BASE}/api/rides/match/start/${matchId}`, {}, { withCredentials: true });
      toast.success("Journey started!");
      setJourneyStarted(true);
    } catch {
      toast.error("Failed to start journey");
    }
  };

  // 3. GPS – only after journey started
  useEffect(() => {
    if (!journeyStarted || !navigator.geolocation) return;

    const watchId = navigator.geolocation.watchPosition(
      pos => {
        const p: [number, number] = [pos.coords.latitude, pos.coords.longitude];
        setUserPos(p);
        setCarPos(p); // car = real GPS position
      },
      err => console.warn("GPS error", err),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 2000 }
    );

    return () => navigator.geolocation.clearWatch(watchId);
  }, [journeyStarted]);

  // 4. Real road route (OSRM) + distance
  useEffect(() => {
    if (!ride || !userPos) return;

    const fetchRoute = async () => {
      const url = `https://router.project-osrm.org/route/v1/driving/${userPos[1]},${userPos[0]};${ride.dropoffLon},${ride.dropoffLat}?overview=full&geometries=geojson`;
      try {
        const res = await axios.get(url);
        const coords = res.data.routes[0].geometry.coordinates.map(
          ([lng, lat]: [number, number]) => L.latLng(lat, lng)
        );
        setRouteCoords(coords);
        setDistanceMeters(res.data.routes[0].distance); // FIXED: Use OSRM driving distance (meters)
      } catch {
        setRouteCoords([
          L.latLng(userPos[0], userPos[1]),
          L.latLng(ride.dropoffLat, ride.dropoffLon),
        ]);
        // Fallback to straight-line
        const from = L.latLng(userPos[0], userPos[1]);
        const to = L.latLng(ride.dropoffLat, ride.dropoffLon);
        setDistanceMeters(from.distanceTo(to));
      }
    };
    fetchRoute();
  }, [ride, userPos]); // Re-fetch on position change for updated route/distance

  useEffect(() => {
    const interval = setInterval(async () => {
      if (userPos && ride?.id) {
        await axios.post(`${API_BASE}/api/rides/${ride.id}/location`, null, { params: { lat: userPos[0], lon: userPos[1] },withCredentials: true });
      }
    }, 20000);
    return () => clearInterval(interval);
  }, [userPos, ride?.id, journeyStarted]);

  // REMOVED: Old straight-line distance useEffect (now handled in fetchRoute)

  // 5. End journey
const endJourney = async () => {
  if (!matchId) return toast.error("No match");

  try {
    const res = await axios.post(
      `${API_BASE}/api/rides/match/end/${matchId}`,
      {},
      { withCredentials: true }
    );

    // Backend now returns: { message?: string, rideId?: number }
    const data = res.data as { message?: string; rideId?: number };

    toast.success(data.message || "Ride completed!");

    if (data.rideId) {
      // Go to feedback page for this ride
      navigate(`/ride/${data.rideId}/feedback`);
    } else {
      // Fallback: go back home if no rideId in response
      navigate("/home");
    }
  } catch (err) {
    console.error("Failed to end ride", err);
    toast.error("Failed to end ride");
  }
};


  if (loading)
    return (
      <div style={{ height: "100vh", display: "grid", placeItems: "center" }}>
        Loading ride…
      </div>
    );

  return (
    <div style={{ height: "100vh", width: "100vw", position: "relative" }}>
      {/* Map – whenReady has correct signature */}
      <MapContainer
        center={userPos || (ride ? [ride.dropoffLat, ride.dropoffLon] : [17.385, 78.4867])}
        zoom={16}
        style={{ height: "100%", width: "100%" }}
        whenReady={() => {
          // This is the correct signature – no parameter needed
          // You can call invalidateSize here if you want
        }}
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

        <MapUpdater position={userPos} /> {/* ADDED: Dynamically recenter on userPos changes */}
      </MapContainer>

      {/* Buttons + distance display */}
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
          <button onClick={endJourney} style={{
            padding: "16px 40px",
            background: "#d32f2f",
            color: "#fff",
            border: "none",
            borderRadius: 30,
            fontSize: 18,
            fontWeight: "bold",
            cursor: "pointer",
          }}>
            End Journey
          </button>
        )}
      </div>

      <ToastContainer position="top-center" />
    </div>
  );
};

export default ActiveRide;