# Smart Watchlist — Frontend

React + Vite dashboard for the Smart Market Watchlist backend. Talks only to
the existing Spring Boot APIs — no data is fabricated client-side.

## Setup

```
cd frontend
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if your backend isn't on :8080
```

## Run

```
npm run dev
```

Opens at `http://localhost:5173`. The backend must be running at the URL in
`.env` (default `http://localhost:8080`) with the `watchlist_db` database
populated (see `data-generator/README.md`).

## Test

```
npm test
```

Runs the Vitest + React Testing Library suite (`src/App.test.jsx`): watchlist
rendering, attention rendering (populated + empty), loading/error states, and
that add/remove/check call the correct backend endpoints with the right
arguments.

## Notes

- `src/config.js` is the only place reading environment variables.
- `src/api/` is the only place calling `fetch` — everything else goes through
  it.
- `POST /check` is only ever called from the "Check for changes" button
  (`WatchlistHeader`) — never on page load or a plain refresh, per the
  product's "viewing shouldn't reset your baseline" requirement.
