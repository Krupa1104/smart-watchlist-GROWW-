import { useCallback, useEffect, useRef, useState } from 'react';
import AppHeader from './components/AppHeader.jsx';
import WatchlistTabs from './components/WatchlistTabs.jsx';
import WatchlistToolbar from './components/WatchlistToolbar.jsx';
import AttentionSection from './components/AttentionSection.jsx';
import WatchlistTable from './components/WatchlistTable.jsx';
import AddItemForm from './components/AddItemForm.jsx';
import { LoadingState, ErrorState, EmptyState } from './components/StatusStates.jsx';
import {
  listWatchlists,
  createWatchlist,
  getWatchlist,
  checkWatchlist,
  detectChanges,
  getAttentionItems,
  addItem,
  removeItem,
  deleteWatchlist,
} from './api/watchlistApi.js';

export default function App() {
  const [summaries, setSummaries] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [watchlist, setWatchlist] = useState(null);
  const [detectedItems, setDetectedItems] = useState([]);
  const [attentionItems, setAttentionItems] = useState([]);

  const [loadingSummaries, setLoadingSummaries] = useState(true);
  const [loadingWatchlist, setLoadingWatchlist] = useState(false);
  const [loadingAttention, setLoadingAttention] = useState(false);
  const [listError, setListError] = useState(null);
  const [watchlistError, setWatchlistError] = useState(null);
  const [attentionError, setAttentionError] = useState(null);

  const [creatingWatchlist, setCreatingWatchlist] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkSummary, setCheckSummary] = useState(null);

  const [addOpen, setAddOpen] = useState(false);
  const [addSubmitting, setAddSubmitting] = useState(false);
  const [addError, setAddError] = useState(null);
  const [addSuccess, setAddSuccess] = useState(null);
  const [prefillSymbol, setPrefillSymbol] = useState('');
  const [editMode, setEditMode] = useState(false);
  const [removingSymbol, setRemovingSymbol] = useState(null);
  const [tableSearch, setTableSearch] = useState('');
  const [deletingWatchlist, setDeletingWatchlist] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  const addSectionRef = useRef(null);

  // --- initial load: fetch the demo user's watchlists ---
  useEffect(() => {
    let cancelled = false;
    async function init() {
      setLoadingSummaries(true);
      setListError(null);
      try {
        const data = await listWatchlists();
        if (cancelled) return;
        setSummaries(data);
        if (data.length > 0) setSelectedId(data[0].id);
      } catch (err) {
        if (!cancelled) setListError(err.message);
      } finally {
        if (!cancelled) setLoadingSummaries(false);
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

  // Pulls BOTH /detect (per-item metrics, incl. non-meaningful ones, used to
  // show a real 1D change/volume figure for every row) and /attention (the
  // meaningful-only digest). Both come straight from the backend — nothing
  // here computes its own "change" number.
  const loadInsights = useCallback(async (id) => {
    setLoadingAttention(true);
    setAttentionError(null);
    try {
      const [detected, attention] = await Promise.all([
        detectChanges(id).catch(() => []), // supplementary; table degrades gracefully if this fails
        getAttentionItems(id),
      ]);
      setDetectedItems(detected);
      setAttentionItems(attention);
    } catch (err) {
      setAttentionError(err.message);
    } finally {
      setLoadingAttention(false);
    }
  }, []);

  // Ordinary data load — never calls /check. Viewing the page must not move
  // the user's "last seen" baseline.
  useEffect(() => {
    if (selectedId == null) return;
    setEditMode(false);
    setTableSearch('');
    setDeleteError(null);
    loadWatchlist(selectedId);
    loadInsights(selectedId);
  }, [selectedId, loadWatchlist, loadInsights]);

  async function handleCreateWatchlist() {
    setCreatingWatchlist(true);
    setListError(null);
    try {
      const created = await createWatchlist();
      setSummaries((prev) => [...prev, { ...created, itemCount: 0 }]);
      setSelectedId(created.id);
    } catch (err) {
      setListError(err.message);
    } finally {
      setCreatingWatchlist(false);
    }
  }

  // The explicit, deliberate "I'm checking now" action.
  async function handleCheck() {
    if (selectedId == null) return;
    setChecking(true);
    setCheckSummary(null);
    try {
      const diffs = await checkWatchlist(selectedId);
      const movedCount = diffs.filter(
        (d) => !d.firstView && d.dataAvailable && d.previousValue !== d.currentValue
      ).length;
      setCheckSummary(
        diffs.length === 0
          ? 'Nothing on this watchlist to check yet.'
          : diffs.every((d) => d.firstView)
          ? 'Baseline recorded — next check will show what changed.'
          : `Checked ${diffs.length} instrument${diffs.length === 1 ? '' : 's'}, ${movedCount} moved since last time.`
      );
      await loadInsights(selectedId);
    } catch (err) {
      setCheckSummary(null);
      setAttentionError(err.message);
    } finally {
      setChecking(false);
    }
  }

  async function handleAddItem(symbol, instrumentType) {
    if (selectedId == null) return;
    setAddSubmitting(true);
    setAddError(null);
    setAddSuccess(null);
    try {
      await addItem(selectedId, symbol, instrumentType);
      setAddSuccess(`${symbol.toUpperCase()} added to your watchlist.`);
      setPrefillSymbol('');
      await Promise.all([loadWatchlist(selectedId), loadInsights(selectedId)]);
    } catch (err) {
      setAddError(err.message);
    } finally {
      setAddSubmitting(false);
    }
  }

  async function handleRemoveItem(symbol) {
    if (selectedId == null) return;
    setRemovingSymbol(symbol);
    try {
      await removeItem(selectedId, symbol);
      await Promise.all([loadWatchlist(selectedId), loadInsights(selectedId)]);
    } catch (err) {
      setWatchlistError(err.message);
    } finally {
      setRemovingSymbol(null);
    }
  }

  // Deletes the whole active watchlist. Confirms first (destructive,
  // unrecoverable), then picks another existing watchlist to show, or falls
  // back to the existing "no watchlist yet" empty state if that was the
  // last one — no separate empty state needed, summaries.length === 0
  // already renders it.
  async function handleDeleteWatchlist() {
    if (selectedId == null || !watchlist) return;
    const confirmed = window.confirm(
      `Delete "${watchlist.name}"? This can't be undone.`
    );
    if (!confirmed) return;

    setDeletingWatchlist(true);
    setDeleteError(null);
    try {
      await deleteWatchlist(selectedId);
      const remaining = summaries.filter((w) => w.id !== selectedId);
      setSummaries(remaining);
      if (remaining.length > 0) {
        setSelectedId(remaining[0].id);
      } else {
        setSelectedId(null);
        setWatchlist(null);
        setAttentionItems([]);
        setDetectedItems([]);
      }
    } catch (err) {
      setDeleteError(err.message);
    } finally {
      setDeletingWatchlist(false);
    }
  }

  function handleGlobalSearch(query) {
    setAddOpen(true);
    setPrefillSymbol(query.toUpperCase());
    addSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  const attentionSymbols = new Set(attentionItems.map((i) => i.symbol));
  const detectedBySymbol = new Map(detectedItems.map((d) => [d.symbol, d]));
  const allItems = watchlist?.items ?? [];
  const filteredItems = tableSearch.trim()
    ? allItems.filter((item) => {
        const q = tableSearch.trim().toLowerCase();
        return (
          item.symbol.toLowerCase().includes(q) ||
          item.marketData?.displayName?.toLowerCase().includes(q) ||
          item.marketData?.groupLabel?.toLowerCase().includes(q)
        );
      })
    : allItems;

  return (
    <div className="app-shell">
      <AppHeader onGlobalSearch={handleGlobalSearch} />

      <main className="page">
        {loadingSummaries && <LoadingState label="Loading your watchlists…" />}

        {!loadingSummaries && listError && (
          <ErrorState message={listError} onRetry={() => window.location.reload()} />
        )}

        {!loadingSummaries && !listError && summaries.length === 0 && (
          <div className="wl-panel">
            <EmptyState title="No watchlist yet" message="Create one to start tracking instruments." />
            <button type="button" className="btn btn--primary" onClick={handleCreateWatchlist}>
              Create watchlist
            </button>
          </div>
        )}

        {!loadingSummaries && !listError && summaries.length > 0 && (
          <>
            <WatchlistTabs
              watchlists={summaries}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onCreate={handleCreateWatchlist}
              creating={creatingWatchlist}
            />

            <AttentionSection
              items={attentionItems}
              loading={loadingAttention}
              error={attentionError}
              onRetry={() => loadInsights(selectedId)}
            />

            {checkSummary && (
              <p className="check-summary" role="status">
                {checkSummary}
              </p>
            )}

            <section className="wl-panel" aria-label="Watchlist">
              <WatchlistToolbar
                itemCount={allItems.length}
                dataAsOf={watchlist?.dataAsOf}
                search={tableSearch}
                onSearchChange={setTableSearch}
                addOpen={addOpen}
                onToggleAdd={() => setAddOpen((v) => !v)}
                editMode={editMode}
                onToggleEdit={() => setEditMode((v) => !v)}
                onCheck={handleCheck}
                checking={checking}
                onDeleteWatchlist={handleDeleteWatchlist}
                deletingWatchlist={deletingWatchlist}
              />

              {deleteError && (
                <p className="wl-panel__error" role="alert">
                  {deleteError}
                </p>
              )}

              {addOpen && (
                <div className="wl-panel__add" ref={addSectionRef}>
                  <AddItemForm
                    onAdd={handleAddItem}
                    submitting={addSubmitting}
                    error={addError}
                    success={addSuccess}
                    prefillSymbol={prefillSymbol}
                  />
                </div>
              )}

              {loadingWatchlist && <LoadingState label="Loading watchlist…" />}
              {!loadingWatchlist && watchlistError && (
                <ErrorState message={watchlistError} onRetry={() => loadWatchlist(selectedId)} />
              )}
              {!loadingWatchlist && !watchlistError && watchlist && (
                <WatchlistTable
                  items={filteredItems}
                  totalCount={allItems.length}
                  attentionSymbols={attentionSymbols}
                  detectedBySymbol={detectedBySymbol}
                  editMode={editMode}
                  onRemove={handleRemoveItem}
                  removingSymbol={removingSymbol}
                />
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}
