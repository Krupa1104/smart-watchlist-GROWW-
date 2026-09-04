// Single source of truth for env-driven config, so nothing else in the app
// reads import.meta.env directly. Falls back to sensible local-dev defaults
// so the app still runs if someone forgets to copy .env.example to .env.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// No auth in the backend yet (see backend README §10) — this stands in for
// "who is asking" and is passed as an explicit query param on every request,
// never silently assumed by the backend.
export const DEMO_USER_ID = Number(import.meta.env.VITE_DEMO_USER_ID) || 1;
