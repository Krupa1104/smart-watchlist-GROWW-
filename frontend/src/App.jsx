import { useCallback, useEffect, useState } from 'react';
import WatchlistHeader from './components/WatchlistHeader.jsx';
import AttentionSection from './components/AttentionSection.jsx';
import WatchlistTable from './components/WatchlistTable.jsx';
import AddItemForm from './components/AddItemForm.jsx';
import { LoadingState, ErrorState, EmptyState } from './components/StatusStates.jsx';
import {
  listWatchlists,
  createWatchlist,
  getWatchlist,
  checkWatchlist,
  getAttentionItems,
  addItem,
  removeItem,
} from './api/watchlistApi.js';

export default function App() {
  const [watchlistId, setWatchlistId] = useState(null);
  const [watchlist, setWatchlist] = useState(null);
  const [attentionItems, setAttentionItems] = useState([]);

  const [loadingWatchlist, setLoadingWatchlist] = useState(true);
  const [loadingAttention, setLoadingAttention] = useState(true);
  const [watchlistError, setWatchlistError] = useState(null);
  const [attentionError, setAttentionError] = useState(null);

  const [checking, setChecking] = useState(false);
  const [checkSummary, setCheckSummary] = useState(null);

  const [addSubmitting, setAddSubmitting] = useState(false);
  const [addError, setAddError] = useState(null);
  const [removingSymbol, setRemovingSymbol] = useState(null);

  // --- initial load: find (or offer to create) the demo user's watchlist ---
  useEffect(() => {
    let cancelled = false;
    async function init() {
      setLoadingWatchlist(true);
      setWatchlistError(null);
      try {
        const summaries = await listWatchlists();
        if (cancelled) return;
        if (summaries.length === 0) {
          setWatchlistId(null);
          setLoadingWatchlist(false);
          setLoadingAttention(false);
          return;
        }
        setWatchlistId(summaries[0].id);
      } catch (err) {
        if (!cancelled) {
          setWatchlistError(err.message);
          setLoadingWatchlist(false);
          setLoadingAttention(false);
        }
      }
    }
    init();
    return () => {
      cancelled = true;
    };
  }, []);

  const loadWatchlist = useCallback(async (id) => {
    setLoadingWatchlist(true);
    setWatchlistError(null);
    try {
      const data = await getWatchlist(id);
      setWatchlist(data);
    } catch (err) {
      setWatchlistError(err.message);
    } finally {
      setLoadingWatchlist(false);
    }
  }, []);

  const loadAttention = useCallback(async (id) => {
    setLoadingAttention(true);
    setAttentionError(null);
    try {
      const data = await getAttentionItems(id);
      setAttentionItems(data);
    } catch (err) {
      setAttentionError(err.message);
    } finally {
      setLoadingAttention(false);
    }
  }, []);

  // Ordinary data load — fires once we know the watchlist id, and whenever
  // it changes. This never calls /check: viewing the page must not silently
  // move the user's "last seen" baseline.
  useEffect(() => {
    if (watchlistId == null) return;
    loadWatchlist(watchlistId);
    loadAttention(watchlistId);
  }, [watchlistId, loadWatchlist, loadAttention]);

  async function handleCreateWatchlist() {
    setLoadingWatchlist(true);
    setWatchlistError(null);
    try {
      const created = await createWatchlist();
      setWatchlistId(created.id);
    } catch (err) {
      setWatchlistError(err.message);
      setLoadingWatchlist(false);
    }
  }

  // The explicit, deliberate "I'm checking now" action. Diffs against the
  // stored snapshot and updates it — only ever triggered by this button.
  async function handleCheck() {
    if (watchlistId == null) return;
    setChecking(true);
    setCheckSummary(null);
    try {
      const diffs = await checkWatchlist(watchlistId);
      const movedCount = diffs.filter(
        (d) => !d.firstView && d.dataAvailable && d.previousValue !== d.currentValue
      ).length;
      setCheckSummary(
        diffs.every((d) => d.firstView)
          ? 'Baseline recorded — next check will show what changed.'
          : `Checked ${diffs.length} instrument${diffs.length === 1 ? '' : 's'}, ${movedCount} moved since last time.`
      );
      // Re-pull attention so "What changed" reflects the freshest detection run too.
      await loadAttention(watchlistId);
    } catch (err) {
      setCheckSummary(null);
      setAttentionError(err.message);
    } finally {
      setChecking(false);
    }
  }

  async function handleAddItem(symbol, instrumentType) {
    if (watchlistId == null) return;
    setAddSubmitting(true);
    setAddError(null);
    try {
      await addItem(watchlistId, symbol, instrumentType);
      await Promise.all([loadWatchlist(watchlistId), loadAttention(watchlistId)]);
    } catch (err) {
      setAddError(err.message);
    } finally {
      setAddSubmitting(false);
    }
  }

  async function handleRemoveItem(symbol) {
    if (watchlistId == null) return;
    setRemovingSymbol(symbol);
    try {
      await removeItem(watchlistId, symbol);
      await Promise.all([loadWatchlist(watchlistId), loadAttention(watchlistId)]);
    } catch (err) {
      setWatchlistError(err.message);
    } finally {
      setRemovingSymbol(null);
    }
  }

  const attentionSymbols = new Set(attentionItems.map((i) => i.symbol));

  if (loadingWatchlist && watchlistId == null && !watchlistError) {
    return (
      <div className="page">
        <LoadingState label="Finding your watchlist…" />
      </div>
    );
  }

  if (watchlistError && watchlist == null) {
    return (
      <div className="page">
        <ErrorState message={watchlistError} onRetry={() => window.location.reload()} />
      </div>
    );
  }

  if (!loadingWatchlist && watchlistId == null) {
    return (
      <div className="page">
        <EmptyState
          title="No watchlist yet"
          message="Create one to start tracking instruments."
        />
        <button type="button" className="btn btn--primary" onClick={handleCreateWatchlist}>
          Create watchlist
        </button>
      </div>
    );
  }

  return (
    <div className="page">
      <WatchlistHeader watchlist={watchlist} onCheck={handleCheck} checking={checking} />

      {checkSummary && <p className="check-summary" role="status">{checkSummary}</p>}

      <AttentionSection
        items={attentionItems}
        loading={loadingAttention}
        error={attentionError}
        onRetry={() => loadAttention(watchlistId)}
      />

      <section className="wl-section" aria-label="All instruments">
        <h2>All instruments</h2>
        {loadingWatchlist && <LoadingState label="Loading watchlist…" />}
        {!loadingWatchlist && watchlistError && (
          <ErrorState message={watchlistError} onRetry={() => loadWatchlist(watchlistId)} />
        )}
        {!loadingWatchlist && !watchlistError && watchlist && (
          <WatchlistTable
            items={watchlist.items}
            attentionSymbols={attentionSymbols}
            onRemove={handleRemoveItem}
            removingSymbol={removingSymbol}
          />
        )}
      </section>

      <section className="wl-section" aria-label="Add instrument">
        <h2>Add an instrument</h2>
        <AddItemForm onAdd={handleAddItem} submitting={addSubmitting} error={addError} />
      </section>
    </div>
  );
}
