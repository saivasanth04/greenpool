import axios from 'axios';

export const API_BASE = import.meta.env.VITE_API_URL || '/api';
export const MINIO_BASE = 'http://localhost:9000/profiles';

export const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  timeout: 10000,
});

// Helper for image URLs
export const getProfileImageUrl = (path?: string) => {
  if (!path) return 'https://via.placeholder.com/50'; // Default placeholder
  return `${MINIO_BASE}/${path}`;
};
