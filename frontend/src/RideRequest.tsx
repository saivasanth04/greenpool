import React, { useEffect, useState, useRef } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { MapContainer, TileLayer, Marker, Popup, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { toast, ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import 'leaflet/dist/leaflet.css';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

// Fix Leaflet default icon paths
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

// Custom icons
const pickupIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
const dropoffIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
const tempIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

interface Location {
  lat: number;
  lon: number;
  address: string;
}

interface RideResponse {
  id: number;
  pickupLat: number;
  pickupLon: number;
  dropoffLat: number;
  dropoffLon: number;
  status: string;
  carbonEstimate: number;
  h3Index: string;
  userId: number;
}

const RideRequest: React.FC = () => {
  const [pickup, setPickup] = useState<Location | null>(null);
  const [dropoff, setDropoff] = useState<Location | null>(null);
  const [pickupSearch, setPickupSearch] = useState<string>('');
  const [dropoffSearch, setDropoffSearch] = useState<string>('');
  const [tempMarker, setTempMarker] = useState<{ lat: number; lon: number } | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const searchTimeout = useRef<NodeJS.Timeout | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (navigator.geolocation) {
      setLoading(true);
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const { latitude: lat, longitude: lon } = position.coords;
          if (!isValidCoord(lat, lon)) {
            setError('Invalid geolocation coordinates.');
            toast.error('Invalid geolocation coordinates.');
            await fallbackToDefaultPickup();
            return;
          }
          const address = await reverseGeocode(lat, lon);
          setPickup({ lat, lon, address });
          setPickupSearch(address);
          if (mapRef.current) mapRef.current.setView([lat, lon], 13);
          setLoading(false);
          toast.success('Pickup set to current location');
        },
        async (err) => {
          console.error('Geolocation error:', err);
          setError('Geolocation access denied or failed. Using default location.');
          toast.warn('Geolocation unavailable. Using default location.');
          await fallbackToDefaultPickup();
          setLoading(false);
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
      );
    } else {
      setError('Geolocation not supported in this browser.');
      toast.error('Geolocation not supported.');
      fallbackToDefaultPickup();
    }
  }, []);

  const fallbackToDefaultPickup = async () => {
    const defaultLat = 17.3850; // Hyderabad
    const defaultLon = 78.4867;
    const address = await reverseGeocode(defaultLat, defaultLon);
    setPickup({ lat: defaultLat, lon: defaultLon, address });
    setPickupSearch(address);
    if (mapRef.current) mapRef.current.setView([defaultLat, defaultLon], 13);
  };

  const isValidCoord = (lat: number, lon: number): boolean => {
    return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
  };

  const reverseGeocode = async (lat: number, lon: number): Promise<string> => {
    for (let i = 0; i < 3; i++) {
      try {
        const response = await axios.get<{ display_name: string }>(`${API_BASE}/api/rides/reverse-geocode`, {
          params: { lat, lon },
          withCredentials: true,
          timeout: 5000, // Added timeout
        });
        return response.data.display_name || 'Unknown location';
      } catch (err: any) {
        console.error(`Reverse geocode attempt ${i + 1} failed:`, err);
        if (i === 2) {
          setError('Failed to fetch address after retries.');
          toast.error('Failed to fetch address.');
          return 'Error fetching address';
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
    return 'Error fetching address';
  };

  const handleSearch = async (query: string, isPickup: boolean) => {
    if (query.length < 3) return;
    if (searchTimeout.current) clearTimeout(searchTimeout.current);
    searchTimeout.current = setTimeout(async () => {
      try {
        setLoading(true);
        console.log('Attempting geocode for:', query);
        const res = await axios.get<{ lat: number; lon: number }>(`${API_BASE}/api/rides/geocode`, {
          params: { address: query },
          withCredentials: true,
          timeout: 5000, // Added timeout
        });
        console.log('Geocode response:', res.data);
        const { lat, lon } = res.data;
        if (!isValidCoord(lat, lon)) {
          toast.error('Invalid location coordinates.');
          return;
        }
        const location: Location = { lat, lon, address: query };
        if (isPickup) {
          setPickup(location);
          setPickupSearch(query);
        } else {
          setDropoff(location);
          setDropoffSearch(query);
        }
        if (mapRef.current) mapRef.current.setView([lat, lon], 13);
        toast.success(`Set ${isPickup ? 'Pickup' : 'Dropoff'} to ${query}`);
      } catch (err: any) {
        toast.error('Location not found. Try a different address.');
      } finally {
        setLoading(false);
      }
    }, 500);
  };

  const MapClickHandler: React.FC = () => {
    useMapEvents({
      click: async (e: L.LeafletMouseEvent) => {
        const { lat, lng: lon } = e.latlng;
        if (!isValidCoord(lat, lon)) {
          toast.error('Invalid map coordinates selected.');
          return;
        }
        setTempMarker({ lat, lon });
      },
    });
    return null;
  };

  const setFromTemp = async (isPickup: boolean) => {
    if (!tempMarker) return;
    setLoading(true);
    const address = await reverseGeocode(tempMarker.lat, tempMarker.lon);
    const location: Location = { lat: tempMarker.lat, lon: tempMarker.lon, address };
    if (isPickup) {
      setPickup(location);
      setPickupSearch(address);
    } else {
      setDropoff(location);
      setDropoffSearch(address);
    }
    if (mapRef.current) mapRef.current.setView([tempMarker.lat, tempMarker.lon], 13);
    setTempMarker(null);
    toast.success(`Set ${isPickup ? 'Pickup' : 'Dropoff'} to ${address}`);
    setLoading(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pickup || !dropoff) {
      setError('Missing pickup or dropoff.');
      toast.error('Please set both pickup and dropoff locations.');
      return;
    }
    if (pickup.lat === dropoff.lat && pickup.lon === dropoff.lon) {
      toast.error('Pickup and dropoff cannot be the same location.');
      return;
    }
    setLoading(true);

    for (let i = 0; i < 3; i++) {
      try {
        const response = await axios.post<RideResponse>(
          `${API_BASE}/api/rides/request`,
          {
            pickupLat: pickup.lat,
            pickupLon: pickup.lon,
            dropoffLat: dropoff.lat,
            dropoffLon: dropoff.lon,
          },
          {
            withCredentials: true,
            timeout: 10000, // Added timeout for POST
          }
        );
        toast.success('Ride request successful!');
        setLoading(false);
        navigate(`/ride/matches/${response.data.id}`);
        return;
      } catch (err: any) {
        console.error(`Ride request attempt ${i + 1} failed:`, err);
        if (err.code === 'ECONNABORTED') {
          setError('Request timed out.');
          toast.error('Request timed out. Please try again.');
        } else if (err.response?.status === 403) {
          setError('Invalid request. Please log in.');
          toast.error('Invalid request. Please log in.');
          navigate('/login');
        } else if (i === 2) {
          setError('Failed to submit ride request after retries.');
          toast.error('Failed to submit ride request.');
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }
    setLoading(false);
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '24px', marginBottom: '20px' }}>Request a Ride</h1>
      {error && <p style={{ color: 'red', marginBottom: '10px' }}>{error}</p>}
      {loading && <p style={{ color: 'blue' }}>Loading...</p>}
      <ToastContainer position="top-right" autoClose={3000} />

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '15px' }}>
          <input
            type="text"
            placeholder="Search Pickup (e.g., Hyderabad)"
            value={pickupSearch}
            onChange={(e) => {
              setPickupSearch(e.target.value);
              handleSearch(e.target.value, true);
            }}
            style={{
              width: '100%',
              padding: '10px',
              fontSize: '16px',
              border: '1px solid #ccc',
              borderRadius: '4px',
            }}
            disabled={loading}
          />
          <p style={{ fontSize: '14px', color: '#555' }}>
            {pickup ? `Pickup: ${pickup.address}` : 'Pickup not set'}
          </p>
        </div>
        <div style={{ marginBottom: '15px' }}>
          <input
            type="text"
            placeholder="Search Dropoff"
            value={dropoffSearch}
            onChange={(e) => {
              setDropoffSearch(e.target.value);
              handleSearch(e.target.value, false);
            }}
            style={{
              width: '100%',
              padding: '10px',
              fontSize: '16px',
              border: '1px solid #ccc',
              borderRadius: '4px',
            }}
            disabled={loading}
          />
          <p style={{ fontSize: '14px', color: '#555' }}>
            {dropoff ? `Dropoff: ${dropoff.address}` : 'Dropoff not set'}
          </p>
        </div>
        <button
          type="submit"
          disabled={loading || !pickup || !dropoff}
          style={{
            width: '100%',
            padding: '12px',
            background: loading || !pickup || !dropoff ? '#ccc' : '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            fontSize: '16px',
            cursor: loading || !pickup || !dropoff ? 'not-allowed' : 'pointer',
          }}
        >
          {loading ? 'Submitting...' : 'Request Ride'}
        </button>
      </form>

      <MapContainer
        center={[17.3850, 78.4867]} // Hyderabad
        zoom={13}
        style={{ height: '400px', marginTop: '20px', borderRadius: '8px' }}
        ref={(map: L.Map | null) => {
          mapRef.current = map;
        }}
      >
        <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        <MapClickHandler />
        {pickup && (
          <Marker position={[pickup.lat, pickup.lon]} icon={pickupIcon}>
            <Popup>Pickup: {pickup.address}</Popup>
          </Marker>
        )}
        {dropoff && (
          <Marker position={[dropoff.lat, dropoff.lon]} icon={dropoffIcon}>
            <Popup>Dropoff: {dropoff.address}</Popup>
          </Marker>
        )}
        {tempMarker && (
          <Marker position={[tempMarker.lat, tempMarker.lon]} icon={tempIcon}>
            <Popup>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <button
                  onClick={() => setFromTemp(true)}
                  style={{ padding: '5px', fontSize: '14px' }}
                >
                  Set as Pickup
                </button>
                <button
                  onClick={() => setFromTemp(false)}
                  style={{ padding: '5px', fontSize: '14px' }}
                >
                  Set as Dropoff
                </button>
              </div>
            </Popup>
          </Marker>
        )}
      </MapContainer>
    </div>
  );
};

export default RideRequest;