// An empty base uses the current origin (and Vite's /api development proxy).
export const API_BASE_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
