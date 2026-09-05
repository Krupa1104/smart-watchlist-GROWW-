import { API_BASE_URL, DEMO_USER_ID } from '../config.js';

// Feature 5: DEMO simulated intraday feed — NOT real market data (see
// backend TickSimulationService). Wrapped in its own module (rather than
// calling `new EventSource(...)` directly from App.jsx) so:
//   - there's exactly one place that knows the URL/event-name contract
//     with the backend's SSE endpoint,
//   - tests can mock this whole module instead of needing a real
//     EventSource implementation (jsdom doesn't have one).
//
// `onBatch` is called with the array of { symbol, instrumentType, value,
// asOfDate, simulated } ticks from a single "tick" SSE event — batched
// backend-side (one event per watchlist per second), so this fires once
// per tick cycle, not once per instrument.
//
// `onStatusChange` (optional) is called with 'connected' or 'reconnecting'
// as the browser's own EventSource connection state changes — see below.
// We do NOT implement any custom polling/reconnect logic here; the
// browser's built-in auto-retry is what's actually reconnecting. This
// callback only surfaces that existing behavior to the UI instead of
// letting a dropped connection look silently still-live.
//
// Returns a cleanup function; call it to close the connection. Never call
// this a second time for the same watchlist without closing the previous
// one first — see App.jsx's effect, which guarantees exactly that.
export function subscribeToLiveTicks(watchlistId, onBatch, onStatusChange, userId = DEMO_USER_ID) {
  const url = `${API_BASE_URL}/api/watchlists/${watchlistId}/live?userId=${userId}`;
  const source = new EventSource(url);

  source.addEventListener('tick', (event) => {
    try {
      const batch = JSON.parse(event.data);
      if (Array.isArray(batch)) onBatch(batch);
    } catch {
      // malformed/partial frame — ignore, the next tick self-corrects
    }
  });

  // Fires on the initial successful connection AND again every time the
  // browser's built-in auto-retry succeeds after a drop — both cases mean
  // "we're live", so both map to the same 'connected' status.
  source.onopen = () => {
    onStatusChange?.('connected');
  };

  // Fires when the connection is lost (server restart, network blip,
  // etc.). The browser keeps retrying on its own — we're not implementing
  // any reconnect/polling logic here, just reflecting that a retry is in
  // progress so the UI can say so instead of quietly looking live.
  source.onerror = () => {
    onStatusChange?.('reconnecting');
  };

  return () => source.close();
}
