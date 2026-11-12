import axios from "axios";
export const API_BASE_URL = import.meta.env.VITE_API_URL || "/api";
export const AUTH_BASE_URL = import.meta.env.VITE_AUTH_URL || "/auth";

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

export const authApi = axios.create({
  baseURL: AUTH_BASE_URL,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;