import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import { toast, ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import L from 'leaflet'; // ADD THIS: For distance fallback
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const MINIO_BASE = 'http://localhost:9000/profiles';

interface RideResponse {
  id: number;
  pickupAddress: string;
  dropoffAddress: string;
  status: string;
  carbonEstimate: number;
  userId: number;

  pickupLat: number;
  pickupLon: number;
  dropoffLat: number;
  dropoffLon: number;
}

interface UserProfile {
  id: number;
  username: string;
  trustScore: number;
  profilePictureUrl: string;
}

interface RideMatchResponse {
  rideId: number;
  matchedRideIds: number[];
  clusterId: number;
}

interface MatchDetail {
  clusterId: number;
  matchedRides: RideResponse[];
}

type DistanceResult = { distanceKm: number };

const RideMatches: React.FC = () => {
  const { rideId } = useParams<{ rideId: string }>();
  const navigate = useNavigate();

  const [ride, setRide] = useState<RideResponse | null>(null);
  const [matches, setMatches] = useState<RideMatchResponse[]>([]);
  const [matchDetails, setMatchDetails] = useState<MatchDetail[]>([]);
  const [users, setUsers] = useState<Map<number, UserProfile>>(new Map());

  const [distances, setDistances] = useState<Map<number, DistanceResult>>(new Map());
  const [currentPos, setCurrentPos] = useState<{ lat: number; lon: number } | null>(null);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // 1) Get current GPS ONCE (no interval)
  useEffect(() => {
    if (!navigator.geolocation) {
      toast.error('Geolocation not supported in this browser.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCurrentPos({ lat: pos.coords.latitude, lon: pos.coords.longitude });
      },
      (err) => {
        console.error(err);
        toast.error('Location permission denied / unavailable.');
        setCurrentPos(null);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }, []);

  useEffect(() => {
    const fetchRideAndMatches = async () => {
      if (!rideId || rideId === 'undefined') {
        setError('Invalid ride ID.');
        toast.error('Invalid ride ID.');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);

        const rideResponse = await axios.get<RideResponse>(
          `${API_BASE}/api/rides/${rideId}`,
          { withCredentials: true }
        );
        setRide(rideResponse.data);

        const matchesResponse = await axios.get<RideMatchResponse[]>(
          `${API_BASE}/api/rides/matches/${rideId}`,
          { withCredentials: true }
        );
        setMatches(matchesResponse.data);

        const details: MatchDetail[] = await Promise.all(
          matchesResponse.data.map(async (match) => {
            const matchedRides = await Promise.all(
              match.matchedRideIds.map(async (id) => {
                try {
                  const res = await axios.get<RideResponse>(
                    `${API_BASE}/api/rides/match/${id}`,
                    { withCredentials: true }
                  );
                  return res.data;
                } catch {
                  return null;
                }
              })
            );

            return {
              clusterId: match.clusterId,
              matchedRides: matchedRides.filter(
                (r): r is RideResponse => r !== null && r.userId !== rideResponse.data.userId
              ),
            };
          })
        );

        const nonEmpty = details.filter((d) => d.matchedRides.length > 0);
        setMatchDetails(nonEmpty);

        // Fetch users after match details
        const userIds = new Set<number>();
        details.forEach((detail) => detail.matchedRides.forEach((r) => userIds.add(r.userId)));

        const userPromises = Array.from(userIds).map((id) =>
          axios
            .get<UserProfile>(`${API_BASE}/api/users/${id}`, { withCredentials: true })
            .then((res) => ({ id, data: res.data }))
        );

        const userResults = await Promise.all(userPromises);
        setUsers(new Map(userResults.map((u) => [u.id, u.data])));
      } catch (err: any) {
        if (err.response?.status === 403 || err.response?.status === 401) {
          setError('Session expired.');
          toast.error('Session expired. Please log in.');
          navigate('/login');
        } else if (err.response?.status === 404) {
          setError('Ride not found.');
          toast.error('Ride not found.');
        } else {
          setError('Failed to load matches.');
          toast.error('Failed to load matches.');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchRideAndMatches();
  }, [rideId, navigate]);

  // 2) Once we have currentPos + ride + matchDetails, compute distances (current → dropoff)
  useEffect(() => {
        const computeDistances = async () => {
      if (!currentPos) return;
      if (!ride && matchDetails.length === 0) return;

      const newDistances = new Map<number, DistanceResult>();

      // Distance for user's own ride
      if (ride) {
        try {
          const url = `https://router.project-osrm.org/route/v1/driving/${currentPos.lon},${currentPos.lat};${ride.dropoffLon},${ride.dropoffLat}?overview=false`;
          const res = await axios.get(url);
          const distanceMeters = res.data.routes[0].distance; // meters
          newDistances.set(ride.id, { distanceKm: distanceMeters / 1000 });
        } catch {
          // Fallback to straight-line
          const from = L.latLng(currentPos.lat, currentPos.lon);
          const to = L.latLng(ride.dropoffLat, ride.dropoffLon);
          const distanceMeters = from.distanceTo(to);
          newDistances.set(ride.id, { distanceKm: distanceMeters / 1000 });
        }
      }

      // Distances for matched rides
      const allRides = matchDetails.flatMap((d) => d.matchedRides);

      const results = await Promise.all(
        allRides.map(async (r) => {
          try {
            const url = `https://router.project-osrm.org/route/v1/driving/${currentPos.lon},${currentPos.lat};${r.dropoffLon},${r.dropoffLat}?overview=false`;
            const res = await axios.get(url);
            const distanceMeters = res.data.routes[0].distance; // meters
            return { rideId: r.id, distanceKm: distanceMeters / 1000 };
          } catch {
            // Fallback to straight-line
            const from = L.latLng(currentPos.lat, currentPos.lon);
            const to = L.latLng(r.dropoffLat, r.dropoffLon);
            const distanceMeters = from.distanceTo(to);
            return { rideId: r.id, distanceKm: distanceMeters / 1000 };
          }
        })
      );

      results.forEach((r) => newDistances.set(r.rideId, { distanceKm: r.distanceKm }));

      setDistances(newDistances);
    };

    computeDistances();
  }, [currentPos, ride, matchDetails]);

  const requestMatch = async (matchedRideId: number) => {
    try {
      await axios.post(
        `${API_BASE}/api/rides/match/request`,
        { rideId: Number(rideId), matchedRideId },
        { withCredentials: true }
      );
      toast.success('Match request sent!');
    } catch {
      toast.error('Failed to send request.');
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '24px', marginBottom: '20px' }}>Potential Ride Matches</h1>

      {loading && <p style={{ color: 'blue' }}>Loading...</p>}
      {error && <p style={{ color: 'red', marginBottom: '10px' }}>{error}</p>}
      <ToastContainer position="top-right" autoClose={3000} />

      {ride && (
        <div style={{ marginBottom: '20px', padding: '10px', border: '1px solid #007bff', borderRadius: '4px' }}>
          <h2>Ride Details</h2>
          <p>Status: {ride.status}</p>
          <p>Pickup: {ride.pickupAddress}</p>
          <p>Dropoff: {ride.dropoffAddress}</p>
          <p>Estimate ratio: 0.2</p>
          <p>Carbon Estimate: {ride.carbonEstimate} mg CO2</p>
          {currentPos && (
            <p style={{ color: '#007bff', fontWeight: 600 }}>
              Distance (you → your dropoff):{' '}
              {distances.get(ride.id)?.distanceKm ?? 'Calculating...'} km
            </p>
          )}
        </div>
      )}

      {!currentPos && (
        <p style={{ color: '#b45309' }}>
          Location not available, so distance to dropoff can’t be calculated.
        </p>
      )}

      {matchDetails.length === 0 ? (
        <p>No potential matches found for this ride.</p>
      ) : (
        matchDetails.map((match, index) => (
          <div
            key={index}
            style={{ marginBottom: '20px', padding: '10px', border: '1px solid #ccc', borderRadius: '4px' }}
          >
            <h3>Cluster {match.clusterId}</h3>
            <p>Potential Matches:</p>

            <ul>
              {match.matchedRides.map((r, i) => {
                const user = users.get(r.userId);
                const dist = distances.get(r.id);

                return (
                  <li key={i} style={{ marginBottom: '12px' }}>
                    Pickup: {r.pickupAddress}, Dropoff: {r.dropoffAddress}
                    <br />
                    Username: {user?.username || 'Loading...'}, Trust Score: {user?.trustScore || 'N/A'}
                    <br />
                    {currentPos && (
                      <span style={{ color: '#007bff', fontWeight: 600 }}>
                        Distance (you → their dropoff):{' '}
                        {dist ? `${dist.distanceKm} km` : 'Calculating...'}
                      </span>
                    )}

                    {user?.profilePictureUrl && (
                      <img
                        src={`${MINIO_BASE}/${user.profilePictureUrl}`}
                        alt="Profile"
                        style={{ width: '50px', height: '50px', borderRadius: '50%', marginLeft: '10px' }}
                      />
                    )}

                    <button onClick={() => requestMatch(r.id)} style={{ marginLeft: '10px' }}>
                      Request Match
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        ))
      )}

      <button
        onClick={() => navigate('/home')}
        style={{
          padding: '10px 20px',
          background: '#007bff',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer',
          marginTop: '10px',
        }}
      >
        Back to Home
      </button>
    </div>
  );
};

export default RideMatches;
