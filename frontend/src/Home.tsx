// frontend/src/pages/Home.tsx
import React, { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast, ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import { api, getProfileImageUrl } from './api'
import { RideResponse, RideMatchRequestResponse, UserProfile } from './types';
import Header from './components/Header';

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
          api.get<RideResponse[]>('/api/rides'),
          api.get<RideMatchRequestResponse[]>('/api/rides/requests/incoming'),
          api.get<RideMatchRequestResponse[]>('/api/rides/matches/confirmed'),
          api.get<RideResponse>('/api/rides/active'),
        ]);

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
          api.get<RideResponse>(`/api/rides/match/${id}`)
            .then((res) => ({ id, data: res.data }))
            .catch(() => null)
        );
        const rideResults = (await Promise.all(ridePromises)).filter((r): r is { id: number; data: RideResponse } => r !== null);
        const rideMap = new Map<number, RideResponse>(rideResults.map((r) => [r.id, r.data]));
        setRequestRides(rideMap);
        setMatchRides(rideMap);

        // Fetch users for requests and matches
        const userIds = new Set<number>();
        rideResults.forEach((r) => userIds.add(r.data.userId));
        const userPromises = Array.from(userIds).map((id) =>
          api.get<UserProfile>(`/api/users/${id}`)
            .then((res) => ({ id, data: res.data }))
            .catch(() => null)
        );
        const userResults = (await Promise.all(userPromises)).filter((u): u is { id: number; data: UserProfile } => u !== null);
        setUsers(new Map<number, UserProfile>(userResults.map((u) => [u.id, u.data])));
      } catch (err: any) {
        toast.error('Session expired. Please log in.');
        navigate('/login');
      } finally {
        setLoading(false);
      }
    };

    fetchData();

    // Poll for active ride every 10s
// In Home.tsx, replace the active ride check with this:

// Poll for active ride every 10s - BUT don't auto-redirect if user is already on active ride page
pollInterval.current = setInterval(async () => {
  try {
    const res = await api.get<RideResponse>('/api/rides/active');
    if (res.data) {
      // Only navigate if we're not already on the active ride page
      const currentPath = window.location.pathname;
      if (!currentPath.includes('/ride/active') && res.data.status !== "COMPLETED") {
        setActiveRide(res.data);
        navigate('/ride/active');
      } else if (res.data.status === "COMPLETED" && !currentPath.includes('/feedback')) {
        // If completed and not on feedback page, go to feedback
        navigate(`/ride/${res.data.id}/feedback`);
      }
    }
  } catch (err) {
    // ignore - don't logout on polling errors
    console.log("Active ride poll error:", err);
  }
}, 10000);

    return () => {
      if (pollInterval.current) clearInterval(pollInterval.current);
    };
  }, [navigate, activeRide]);

  // Actions
  const handleRequestAction = async (requestId: number, action: 'confirm' | 'reject') => {
    try {
      await api.post(`/api/rides/match/${action}/${requestId}`);
      toast.success(`Match ${action}ed!`);
      window.location.reload();
    } catch {
      toast.error(`Failed to ${action} match.`);
    }
  };

  const startJourney = async (requestId: number) => {
    try {
      await api.post(`/api/rides/match/start/${requestId}`);
      toast.success('Start confirmed. Waiting for partner.');
      window.location.reload();
    } catch {
      toast.error('Failed to start journey.');
    }
  };

  return (
    <>
      <Header />
      <div className="gp-container">
        <ToastContainer position="top-right" autoClose={3000} />

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
          <h2>EcoRide Hub</h2>
          <Link to="/ride/request">
            <button className="gp-btn" style={{ width: 'auto' }}>
              Request New Ride
            </button>
          </Link>
        </div>

        {loading && <p>Loading...</p>}

        <div className="gp-card">
          <h3>Your Rides</h3>
          {rides.length === 0 ? (
            <p>No rides found. Request a ride to get started!</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0 }}>
              {rides.map((ride) => (
                <li key={ride.id} style={{ padding: '15px', borderBottom: '1px solid #eee' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <strong>Ride #{ride.id}</strong> <span style={{ fontSize: '0.8em', color: '#666' }}>{ride.status}</span>
                      <div>From: {ride.pickupAddress}</div>
                      <div>To: {ride.dropoffAddress}</div>
                    </div>
                    <button
                      className="gp-btn gp-btn-secondary"
                      style={{ width: 'auto', padding: '8px 12px', fontSize: '0.9rem' }}
                      onClick={() => navigate(`/ride/matches/${ride.id}`)}
                    >
                      Find Matches
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="gp-card">
          <h3>Pending Requests</h3>
          {incomingRequests.length === 0 ? (
            <p>No pending requests.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0 }}>
              {incomingRequests.map((req) => {
                const fromRide = requestRides.get(req.fromRideId);
                const user = fromRide ? users.get(fromRide.userId) : null;
                return (
                  <li key={req.id} style={{ padding: '15px', borderBottom: '1px solid #eee' }}>
                    <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                      {user && <img src={getProfileImageUrl(user.profilePictureUrl)} alt="Profile" style={{ width: '40px', height: '40px', borderRadius: '50%' }} />}
                      <div style={{ flex: 1 }}>
                        <div><strong>{user?.username}</strong> wants to match. (Trust: {user?.trustScore})</div>
                        <div style={{ fontSize: '0.9rem' }}>Pickup: {fromRide?.pickupAddress}</div>
                      </div>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <button className="gp-btn" style={{ width: 'auto', padding: '5px 10px' }} onClick={() => handleRequestAction(req.id, 'confirm')}>Accept</button>
                        <button className="gp-btn gp-btn-secondary" style={{ width: 'auto', padding: '5px 10px', background: '#d32f2f' }} onClick={() => handleRequestAction(req.id, 'reject')}>Reject</button>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="gp-card">
          <h3>Confirmed Matches</h3>
          {confirmedMatches.length === 0 ? (
            <p>No active matches.</p>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0 }}>
              {confirmedMatches.map((match) => {
                const isToUser = rides.some((r) => r.id === match.toRideId);
                const partnerRideId = isToUser ? match.fromRideId : match.toRideId;
                const partnerRide = matchRides.get(partnerRideId);
                const user = partnerRide ? users.get(partnerRide.userId) : null;
                const canStart = match.status === 'CONFIRMED';

                return (
                  <li key={match.id} style={{ padding: '15px', borderBottom: '1px solid #eee' }}>
                    <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                      {user && <img src={getProfileImageUrl(user.profilePictureUrl)} alt="Profile" style={{ width: '40px', height: '40px', borderRadius: '50%' }} />}
                      <div style={{ flex: 1 }}>
                        <div>Partner: <strong>{user?.username}</strong> (Trust: {user?.trustScore})</div>
                        <div style={{ fontSize: '0.9rem' }}>Destination: {partnerRide?.dropoffAddress}</div>
                      </div>
                      {canStart && (
                        <button
                          className="gp-btn"
                          style={{ width: 'auto', background: 'var(--primary-color)' }}
                          onClick={() => startJourney(match.id)}
                        >
                          Start Journey
                        </button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="text-center mb-4">
          <button
            className="gp-btn gp-btn-secondary"
            style={{ width: 'auto' }}
            onClick={() => navigate('/parent/signup')}
          >
            👨‍👩‍👧 Register as Parent
          </button>
        </div>
      </div>
    </>
  );
};

export default Home;