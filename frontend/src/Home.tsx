// frontend/src/pages/Home.tsx
import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { toast, ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const MINIO_BASE = 'http://localhost:9000/profiles';

interface RideResponse {
  id: number;
  pickupAddress: string;
  dropoffAddress: string;
  status: string;
  carbonEstimate: number;
  userId: number;
  h3Index?: string;
}

interface RideMatchRequestResponse {
  id: number;
  fromRideId: number;
  toRideId: number;
  status: string;
}

interface UserProfile {
  id: number;
  username: string;
  trustScore: number;
  profilePictureUrl: string;
}

const Home: React.FC = () => {
  const [rides, setRides] = useState<RideResponse[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<RideMatchRequestResponse[]>([]);
  const [confirmedMatches, setConfirmedMatches] = useState<RideMatchRequestResponse[]>([]);
  const [users, setUsers] = useState<Map<number, UserProfile>>(new Map());
  const [requestRides, setRequestRides] = useState<Map<number, RideResponse>>(new Map());
  const [matchRides, setMatchRides] = useState<Map<number, RideResponse>>(new Map());
  const [loading, setLoading] = useState<boolean>(true);
  const [activeRide, setActiveRide] = useState<RideResponse | null>(null);
  const pollInterval = useRef<NodeJS.Timeout | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const [ridesRes, incomingRes, confirmedRes, activeRes] = await Promise.all([
          axios.get<RideResponse[]>(`${API_BASE}/api/rides`, { withCredentials: true }),
          axios.get<RideMatchRequestResponse[]>(`${API_BASE}/api/rides/requests/incoming`, { withCredentials: true }),
          axios.get<RideMatchRequestResponse[]>(`${API_BASE}/api/rides/matches/confirmed`, { withCredentials: true }),
          axios.get<RideResponse>(`${API_BASE}/api/rides/active`, { withCredentials: true }),
        ]);

        console.log('Rides Response:', ridesRes.data);

        const visibleRides = ridesRes.data.filter(
  (r) => r.status !== 'COMPLETED'
);

setRides(visibleRides);
        setIncomingRequests(incomingRes.data);
        setConfirmedMatches(confirmedRes.data);
        setActiveRide(activeRes.data);

        if (activeRes.data) {
          navigate('/ride/active');
          return;
        }

        // Fetch unique ride IDs from requests and matches
        const rideIds = new Set<number>();
        incomingRes.data.forEach((req) => rideIds.add(req.fromRideId));
        confirmedRes.data.forEach((match) => {
          rideIds.add(match.fromRideId);
          rideIds.add(match.toRideId);
        });

        const ridePromises = Array.from(rideIds).map((id) =>
          axios.get<RideResponse>(`${API_BASE}/api/rides/match/${id}`, { withCredentials: true })
            .then((res) => {
              console.log(`Ride ${id} Response:`, res.data);
              return { id, data: res.data };
            })
            .catch((err) => {
              console.error(`Failed to fetch ride ${id}:`, err);
              return null;
            })
        );
        const rideResults = (await Promise.all(ridePromises)).filter((r): r is { id: number; data: RideResponse } => r !== null);
        const rideMap = new Map<number, RideResponse>(rideResults.map((r) => [r.id, r.data]));
        setRequestRides(rideMap);
        setMatchRides(rideMap);

        // Fetch users for requests and matches
        const userIds = new Set<number>();
        rideResults.forEach((r) => userIds.add(r.data.userId));
        const userPromises = Array.from(userIds).map((id) =>
          axios.get<UserProfile>(`${API_BASE}/api/users/${id}`, { withCredentials: true })
            .then((res) => {
              console.log(`User ${id} Response:`, res.data);
              return { id, data: res.data };
            })
            .catch((err) => {
              console.error(`Failed to fetch user ${id}:`, err);
              return null;
            })
        );
        const userResults = (await Promise.all(userPromises)).filter((u): u is { id: number; data: UserProfile } => u !== null);
        setUsers(new Map<number, UserProfile>(userResults.map((u) => [u.id, u.data])));
      } catch (err: any) {
        console.error('Fetch Data Error:', err);
        toast.error('Session expired. Please log in.');
        navigate('/login');
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    // Poll for active ride every 10s
    pollInterval.current = setInterval(async () => {
      try {
        const res = await axios.get<RideResponse>(`${API_BASE}/api/rides/active`, { withCredentials: true });
        if (res.data && !activeRide) {
          setActiveRide(res.data);
          navigate('/ride/active');
        }
      } catch (err) {
        console.error('Poll error:', err);
      }
    }, 10000);

    return () => {
      if (pollInterval.current) clearInterval(pollInterval.current);
    };
  }, [navigate, activeRide]);

  const viewMatches = (rideId: number) => {
    if (!rideId) {
      toast.error('Invalid ride ID');
      return;
    }
    navigate(`/ride/matches/${rideId}`);
  };

  const confirmRequest = async (requestId: number) => {
    try {
      await axios.post(`${API_BASE}/api/rides/match/confirm/${requestId}`, {}, { withCredentials: true });
      toast.success('Match confirmed!');
      window.location.reload();
    } catch {
      toast.error('Failed to confirm match.');
    }
  };

  const rejectRequest = async (requestId: number) => {
    try {
      await axios.post(`${API_BASE}/api/rides/match/reject/${requestId}`, {}, { withCredentials: true });
      toast.success('Match rejected.');
      window.location.reload();
    } catch {
      toast.error('Failed to reject match.');
    }
  };

  const startJourney = async (requestId: number) => {
    try {
      await axios.post(`${API_BASE}/api/rides/match/start/${requestId}`, {}, { withCredentials: true });
      toast.success('Start confirmed. Waiting for partner.');
      window.location.reload();
    } catch {
      toast.error('Failed to start journey.');
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
      <h2>EcoRide Hub</h2>
      {loading && <p>Loading...</p>}
      <ToastContainer position="top-right" autoClose={3000} />
      <div style={{ marginBottom: '20px' }}>
        <Link to="/ride/request">
          <button style={{ padding: '10px', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', marginRight: '10px' }}>
            Request Ride
          </button>
        </Link>
      </div>
      <h3>Your Rides</h3>
      {rides.length === 0 ? (
        <p>No rides found. Request a ride to get started!</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {rides.map((ride) => (
            ride.id ? (
              <li key={ride.id} style={{ padding: '10px', border: '1px solid #ccc', marginBottom: '10px', borderRadius: '4px' }}>
                <p>Ride ID: {ride.id}</p>
                <p>Status: {ride.status}</p>
                <p>Pickup: {ride.pickupAddress}</p>
                <p>Dropoff: {ride.dropoffAddress}</p>
                <p>User ID: {ride.userId}</p>
                <p>Carbon Estimate: {ride.carbonEstimate} kg CO2</p>
                <button
                  onClick={() => viewMatches(ride.id)}
                  style={{ padding: '5px 10px', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px' }}
                >
                  View Potential Matches
                </button>
              </li>
            ) : null
          ))}
        </ul>
      )}
      <h3>Pending Incoming Requests</h3>
      {incomingRequests.length === 0 ? (
        <p>No pending requests.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {incomingRequests.map((req) => {
            const fromRide = requestRides.get(req.fromRideId);
            const user = fromRide ? users.get(fromRide.userId) : null;
            return (
              <li key={req.id} style={{ padding: '10px', border: '1px solid #ccc', marginBottom: '10px', borderRadius: '4px' }}>
                <p>From Ride Pickup: {fromRide?.pickupAddress || 'Unknown'}</p>
                <p>From Ride Dropoff: {fromRide?.dropoffAddress || 'Unknown'}</p>
                {user ? (
                  <>
                    <p>Requester: {user.username} (Trust: {user.trustScore})</p>
                    <img src={`${MINIO_BASE}/${user.profilePictureUrl}`} alt="Profile" style={{ width: '50px', height: '50px', borderRadius: '50%' }} />
                  </>
                ) : (
                  <p>Requester: Unknown</p>
                )}
                <button onClick={() => confirmRequest(req.id)} style={{ marginRight: '10px' }}>Confirm</button>
                <button onClick={() => rejectRequest(req.id)}>Reject</button>
              </li>
            );
          })}
        </ul>
      )}
      <h3>Confirmed Matches</h3>
      {confirmedMatches.length === 0 ? (
        <p>No confirmed matches.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {confirmedMatches.map((match) => {
            const isToUser = rides.some((r) => r.id === match.toRideId);
            const partnerRideId = isToUser ? match.fromRideId : match.toRideId;
            const partnerRide = matchRides.get(partnerRideId);
            const user = partnerRide ? users.get(partnerRide.userId) : null;
            return (
              <li key={match.id} style={{ padding: '10px', border: '1px solid #ccc', marginBottom: '10px', borderRadius: '4px' }}>
                <p>Partner Pickup: {partnerRide?.pickupAddress || 'Unknown'}</p>
                <p>Partner Dropoff: {partnerRide?.dropoffAddress || 'Unknown'}</p>
                {user ? (
                  <>
                    <p>Partner: {user.username} (Trust: {user.trustScore})</p>
                    <img src={`${MINIO_BASE}/${user.profilePictureUrl}`} alt="Profile" style={{ width: '50px', height: '50px', borderRadius: '50%' }} />
                  </>
                ) : (
                  <p>Partner: Unknown</p>
                )}
                {match.status === 'CONFIRMED' && (
                  <button
                    onClick={() => startJourney(match.id)}
                    style={{ padding: '5px 10px', background: '#28a745', color: 'white', border: 'none', borderRadius: '4px', marginLeft: '10px' }}
                  >
                    Start Journey
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}

<div style={{ margin: '20px' }}>
  <button 
    onClick={() => navigate('/parent/signup')}
    style={{ padding: '12px 24px', fontSize: '16px' }}
  >
    👨‍👩‍👧 Register as Parent
  </button>
</div>
    </div>
  );
};

export default Home;