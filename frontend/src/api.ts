// src/api.ts
import axios from "axios";

const api = axios.create({
  baseURL: "/api", // works with Vite dev proxy and nginx /api proxy
  withCredentials: true,
});

// Global response interceptor: handle auth/session errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;

    if (status === 401 || status === 403 || status === 500) {
      console.warn("Auth error, clearing session", status);

      // Expire backend JWT cookie
      document.cookie = "jwt=; Max-Age=0; path=/; SameSite=Lax";

      // Clear any local session state
      localStorage.removeItem("avatar");
      localStorage.removeItem("username");

      // Redirect to login if not already there
      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }

    return Promise.reject(error);
  }
);

export default api;
