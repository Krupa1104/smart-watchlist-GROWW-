import { apiClient } from './client.js';
import { DEMO_USER_ID } from '../config.js';

// One function per backend endpoint (see WatchlistController.java). Every
// call carries userId as a query param, matching the backend's no-auth
// contract — nothing here pretends this is real authentication.

export function listWatchlists(userId = DEMO_USER_ID) {
  return apiClient.get('/api/watchlists', { userId });
}

export function createWatchlist(name, userId = DEMO_USER_ID) {
  return apiClient.post('/api/watchlists', { userId }, name ? { name } : undefined);
}

export function getWatchlist(watchlistId, userId = DEMO_USER_ID) {
  return apiClient.get(`/api/watchlists/${watchlistId}`, { userId });
}

// The explicit "check now" action — diffs against the last snapshot and
// updates it. Must only be called on a deliberate user action, never on
// ordinary page load/render (see App.jsx).
export function checkWatchlist(watchlistId, userId = DEMO_USER_ID) {
  return apiClient.post(`/api/watchlists/${watchlistId}/check`, { userId });
}

export function detectChanges(watchlistId, userId = DEMO_USER_ID) {
  return apiClient.get(`/api/watchlists/${watchlistId}/detect`, { userId });
}

// The actual "what deserves your attention" digest — meaningful changes
// only, highest severity first (backend already sorts this).
export function getAttentionItems(watchlistId, userId = DEMO_USER_ID) {
  return apiClient.get(`/api/watchlists/${watchlistId}/attention`, { userId });
}

export function addItem(watchlistId, symbol, instrumentType, userId = DEMO_USER_ID) {
  return apiClient.post(`/api/watchlists/${watchlistId}/items`, { userId }, {
    symbol,
    instrumentType,
  });
}

export function removeItem(watchlistId, symbol, userId = DEMO_USER_ID) {
  return apiClient.delete(`/api/watchlists/${watchlistId}/items/${symbol}`, { userId });
}

// Deletes the whole watchlist. The backend cascades this to the
// watchlist's own items/snapshots at the database level — see
// backend/.../service/WatchlistService.java.
export function deleteWatchlist(watchlistId, userId = DEMO_USER_ID) {
  return apiClient.delete(`/api/watchlists/${watchlistId}`, { userId });
}
