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
// Returns a cleanup function; call it to close the connection. Never call
// this a second time for the same watchlist without closing the previous
// one first — see App.jsx's effect, which guarantees exactly that.
export function subscribeToLiveTicks(watchlistId, onBatch, userId = DEMO_USER_ID) {
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

  // EventSource reconnects automatically on transient network errors by
  // default; nothing extra is needed for a demo-scale feed. onerror is
  // intentionally left unhandled beyond the browser's built-in retry.

  return () => source.close();
}
