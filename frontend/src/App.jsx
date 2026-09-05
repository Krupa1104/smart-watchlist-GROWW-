import { useCallback, useEffect, useRef, useState } from 'react';
import AppHeader from './components/AppHeader.jsx';
import WatchlistTabs from './components/WatchlistTabs.jsx';
import WatchlistToolbar from './components/WatchlistToolbar.jsx';
import AttentionSection from './components/AttentionSection.jsx';
import WatchlistTable from './components/WatchlistTable.jsx';
import AddItemForm from './components/AddItemForm.jsx';
import CreateWatchlistModal from './components/CreateWatchlistModal.jsx';
import SinceLastCheckPanel from './components/SinceLastCheckPanel.jsx';
import SeverityLegend from './components/SeverityLegend.jsx';
import InstrumentDetailPanel from './components/InstrumentDetailPanel.jsx';
import { LoadingState, ErrorState, EmptyState } from './components/StatusStates.jsx';
import {
  listWatchlists,
  listInstruments,
  createWatchlist,
  getWatchlist,
  checkWatchlist,
  detectChanges,
  getAttentionItems,
  addItem,
  removeItem,
  deleteWatchlist,
} from './api/watchlistApi.js';
import { subscribeToLiveTicks } from './api/liveFeed.js';

export default function App() {
  const [summaries, setSummaries] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [watchlist, setWatchlist] = useState(null);
  const [detectedItems, setDetectedItems] = useState([]);
  const [attentionItems, setAttentionItems] = useState([]);
  const [instruments, setInstruments] = useState([]);

  const [loadingSummaries, setLoadingSummaries] = useState(true);
  const [loadingWatchlist, setLoadingWatchlist] = useState(false);
  const [loadingAttention, setLoadingAttention] = useState(false);
  const [listError, setListError] = useState(null);
  const [watchlistError, setWatchlistError] = useState(null);
  const [attentionError, setAttentionError] = useState(null);

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creatingWatchlist, setCreatingWatchlist] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [checking, setChecking] = useState(false);
  const [checkSummary, setCheckSummary] = useState(null);
  const [checkDiffs, setCheckDiffs] = useState([]);
  const [detailSymbol, setDetailSymbol] = useState(null);
  const [liveValues, setLiveValues] = useState(new Map());
  const [liveStatus, setLiveStatus] = useState('connecting'); // 'connecting' | 'connected' | 'reconnecting'

  const [addOpen, setAddOpen] = useState(false);
  const [addSubmitting, setAddSubmitting] = useState(false);
  const [addError, setAddError] = useState(null);
  const [addSuccess, setAddSuccess] = useState(null);
  const [prefillSymbol, setPrefillSymbol] = useState('');
  const [prefillInstrumentType, setPrefillInstrumentType] = useState('');
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

  // Full stock/fund reference list for the global search — fetched once,
  // filtered client-side per keystroke (see AppHeader.jsx). Failure here is
  // non-fatal: the rest of the app works fine, global search just quietly
  // has nothing to show until a retry (e.g. next reload).
  useEffect(() => {
    let cancelled = false;
    listInstruments()
      .then((data) => {
        if (!cancelled) setInstruments(data);
      })
      .catch(() => {
        // supplementary data only — no error state needed for this
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Feature 5: DEMO simulated intraday feed — exactly ONE SSE connection
  // per active watchlist, opened here (not in a child component) so it
  // can never be accidentally duplicated by e.g. the detail panel mounting
  // its own. Reset the displayed live values and (re)subscribe whenever
  // the selected watchlist changes; the returned cleanup function closes
  // the previous EventSource before React ever opens a new one, and also
  // runs on unmount.
  useEffect(() => {
    setLiveValues(new Map());
    setLiveStatus('connecting');
    if (selectedId == null) return undefined;
    const unsubscribe = subscribeToLiveTicks(
      selectedId,
      (batch) => {
        setLiveValues((prev) => {
          const next = new Map(prev);
          batch.forEach((t) => {
            next.set(t.symbol, { value: t.value, asOfDate: t.asOfDate, instrumentType: t.instrumentType });
          });
          return next;
        });
      },
      (status) => setLiveStatus(status)
    );
    return unsubscribe;
  }, [selectedId]);

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
    setCheckSummary(null);
    setCheckDiffs([]);
    setDetailSymbol(null);
    loadWatchlist(selectedId);
    loadInsights(selectedId);
  }, [selectedId, loadWatchlist, loadInsights]);

  // "+ Watchlist" (and the empty-state "Create watchlist" button) now open
  // a naming dialog instead of silently creating "My Watchlist" — see
  // CreateWatchlistModal. Nothing is created until the user confirms there.
  function handleOpenCreateModal() {
    setCreateError(null);
    setCreateModalOpen(true);
  }

  function handleCancelCreateModal() {
    if (creatingWatchlist) return; // don't let an overlay click abandon an in-flight request
    setCreateModalOpen(false);
  }

  async function handleConfirmCreateWatchlist(name) {
    setCreatingWatchlist(true);
    setCreateError(null);
    try {
      const created = await createWatchlist(name);
      setSummaries((prev) => [...prev, { ...created, itemCount: 0 }]);
      setSelectedId(created.id);
      setCreateModalOpen(false);
    } catch (err) {
      setCreateError(err.message);
    } finally {
      setCreatingWatchlist(false);
    }
  }

  // Belt-and-suspenders recovery for Issue 3 (stale watchlist ids): if an
  // action against the currently-selected watchlist comes back 404, the id
  // we're holding no longer exists on the backend — most likely deleted
  // outside this session (another tab, direct API testing, etc.), since the
  // app's own delete flow already keeps selectedId/summaries in sync. This
  // re-syncs the tab list and drops the dead selection so a stale id can't
  // be used for a subsequent action. Only runs on an actual 404, so it adds
  // no extra requests during normal use.
  const recoverFromStaleWatchlist = useCallback(async (err, staleId) => {
    if (err?.status !== 404) return;
    try {
      const fresh = await listWatchlists();
      setSummaries(fresh);
      setSelectedId((prev) => {
        if (prev !== staleId) return prev; // selection already moved on elsewhere; don't fight it
        return fresh.some((w) => w.id === staleId) ? prev : fresh[0]?.id ?? null;
      });
    } catch {
      // best-effort recovery only — leave the original error visible either way
    }
  }, []);

  // The explicit, deliberate "I'm checking now" action.
  async function handleCheck() {
    if (selectedId == null) return;
    setChecking(true);
    setCheckSummary(null);
    try {
      const diffs = await checkWatchlist(selectedId);
      setCheckDiffs(diffs);
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
      setCheckDiffs([]);
      setAttentionError(err.message);
      await recoverFromStaleWatchlist(err, selectedId);
    } finally {
      setChecking(false);
    }
  }

  async function handleAddItem(symbol, instrumentType) {
    if (selectedId == null) return;
    const targetId = selectedId;
    setAddSubmitting(true);
    setAddError(null);
    setAddSuccess(null);
    try {
      await addItem(targetId, symbol, instrumentType);
      setAddSuccess(`${symbol.toUpperCase()} added to your watchlist.`);
      setPrefillSymbol('');
      setPrefillInstrumentType('');
      await Promise.all([loadWatchlist(targetId), loadInsights(targetId)]);
    } catch (err) {
      setAddError(err.message);
      await recoverFromStaleWatchlist(err, targetId);
    } finally {
      setAddSubmitting(false);
    }
  }

  async function handleRemoveItem(symbol) {
    if (selectedId == null) return;
    const targetId = selectedId;
    setRemovingSymbol(symbol);
    try {
      await removeItem(targetId, symbol);
      await Promise.all([loadWatchlist(targetId), loadInsights(targetId)]);
    } catch (err) {
      setWatchlistError(err.message);
      await recoverFromStaleWatchlist(err, targetId);
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

  // Picking a result from the global instrument search (AppHeader) opens
  // the add form prefilled with that instrument — it does NOT add anything
  // by itself. Adding still requires the explicit "Add to watchlist" click.
  function handleSelectInstrument(instrument) {
    setAddOpen(true);
    setPrefillSymbol(instrument.symbol);
    setPrefillInstrumentType(instrument.instrumentType);
    addSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  // Clicking a row in the watchlist table opens the instrument detail
  // panel — a read-only view (see InstrumentDetailPanel/backend
  // getInstrumentDetail), never mutates anything on its own.
  function handleOpenDetail(symbol) {
    setDetailSymbol(symbol);
  }

  function handleCloseDetail() {
    setDetailSymbol(null);
  }

  const attentionSymbols = new Set(attentionItems.map((i) => i.symbol));
  const detectedBySymbol = new Map(detectedItems.map((d) => [d.symbol, d]));
  const allItems = watchlist?.items ?? [];
  const displayNameBySymbol = new Map(allItems.map((i) => [i.symbol, i.marketData?.displayName]));
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
      <AppHeader instruments={instruments} onSelectInstrument={handleSelectInstrument} />

      <CreateWatchlistModal
        open={createModalOpen}
        submitting={creatingWatchlist}
        error={createError}
        onCancel={handleCancelCreateModal}
        onConfirm={handleConfirmCreateWatchlist}
      />

      <main className="page">
        {loadingSummaries && <LoadingState label="Loading your watchlists…" />}

        {!loadingSummaries && listError && (
          <ErrorState message={listError} onRetry={() => window.location.reload()} />
        )}

        {!loadingSummaries && !listError && summaries.length === 0 && (
          <div className="wl-panel">
            <EmptyState title="No watchlist yet" message="Create one to start tracking instruments." />
            <button type="button" className="btn btn--primary" onClick={handleOpenCreateModal}>
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
              onCreate={handleOpenCreateModal}
              creating={creatingWatchlist}
            />

            <AttentionSection
              items={attentionItems}
              loading={loadingAttention}
              error={attentionError}
              onRetry={() => loadInsights(selectedId)}
            />
            <SeverityLegend />

            {checkSummary && (
              <p className="check-summary" role="status">
                {checkSummary}
              </p>
            )}

            {checkDiffs.length > 0 && (
              <SinceLastCheckPanel
                diffs={checkDiffs}
                detectedBySymbol={detectedBySymbol}
                displayNameBySymbol={displayNameBySymbol}
              />
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
                liveFeedActive={liveValues.size > 0}
                liveStatus={liveStatus}
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
                    prefillInstrumentType={prefillInstrumentType}
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
                  onSelectInstrument={handleOpenDetail}
                  liveValues={liveValues}
                />
              )}
            </section>
          </>
        )}
      </main>

      {detailSymbol && selectedId != null && (
        <InstrumentDetailPanel
          watchlistId={selectedId}
          symbol={detailSymbol}
          onClose={handleCloseDetail}
          liveValue={liveValues.get(detailSymbol)}
        />
      )}
    </div>
  );
}
