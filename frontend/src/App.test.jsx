import { render, screen, waitFor, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from './App.jsx';
import * as api from './api/watchlistApi.js';

vi.mock('./api/watchlistApi.js');

const SUMMARIES = [{ id: 1, name: 'My Watchlist', createdAt: '2026-08-24T10:00:00Z', itemCount: 2 }];

const WATCHLIST = {
  id: 1,
  userId: 1,
  name: 'My Watchlist',
  createdAt: '2026-08-24T10:00:00Z',
  dataAsOf: '2026-08-24',
  items: [
    {
      itemId: 10,
      symbol: 'STK01',
      instrumentType: 'STOCK',
      addedAt: '2026-08-01T10:00:00Z',
      marketData: {
        symbol: 'STK01',
        instrumentType: 'STOCK',
        displayName: 'Acme Corp',
        groupLabel: 'IT',
        latestValue: 3570.5,
        asOfDate: '2026-08-24',
        dataAvailable: true,
      },
    },
    {
      itemId: 11,
      symbol: 'FUND01',
      instrumentType: 'FUND',
      addedAt: '2026-08-01T10:00:00Z',
      marketData: {
        symbol: 'FUND01',
        instrumentType: 'FUND',
        displayName: 'Growth Fund',
        groupLabel: 'Equity',
        latestValue: 42.1,
        asOfDate: '2026-08-24',
        dataAvailable: true,
      },
    },
  ],
};

const DETECTED = [
  {
    symbol: 'STK01',
    instrumentType: 'STOCK',
    asOfDate: '2026-08-24',
    meaningful: true,
    changeType: 'return_z_score',
    severityScore: 3.2,
    explanation: 'STK01 moved 9.1% versus its normal daily range.',
    metrics: { dailyReturn: 0.091, returnZScore: 3.2 },
  },
  {
    symbol: 'FUND01',
    instrumentType: 'FUND',
    asOfDate: '2026-08-24',
    meaningful: false,
    changeType: null,
    severityScore: null,
    explanation: 'Tracking its category peers normally — no action needed.',
    metrics: { fundChangePct: 0.001, categoryAvgChangePct: 0.002 },
  },
];

const ATTENTION_ITEM = {
  symbol: 'STK01',
  instrumentType: 'STOCK',
  asOfDate: '2026-08-24',
  changeType: 'return_z_score',
  severity: 3.2,
  explanation: 'STK01 moved 9.1% versus its normal daily range.',
  metrics: { dailyReturn: 0.091, returnZScore: 3.2 },
};

const INSTRUMENTS = [
  { symbol: 'STK01', instrumentType: 'STOCK', name: 'Acme Corp', groupLabel: 'IT' },
  { symbol: 'STK09', instrumentType: 'STOCK', name: 'Beta Industries', groupLabel: 'Energy' },
  { symbol: 'FUND01', instrumentType: 'FUND', name: 'Growth Fund', groupLabel: 'Equity' },
];

function setupDefaultMocks({ attention = [], detected = DETECTED, summaries = SUMMARIES } = {}) {
  api.listWatchlists.mockResolvedValue(summaries);
  api.listInstruments.mockResolvedValue(INSTRUMENTS);
  api.getWatchlist.mockResolvedValue(WATCHLIST);
  api.detectChanges.mockResolvedValue(detected);
  api.getAttentionItems.mockResolvedValue(attention);
  api.checkWatchlist.mockResolvedValue([]);
  api.addItem.mockResolvedValue({});
  api.removeItem.mockResolvedValue(undefined);
  api.deleteWatchlist.mockResolvedValue(undefined);
  api.createWatchlist.mockResolvedValue({ id: 2, name: 'New Watchlist', createdAt: '2026-08-24T00:00:00Z' });
}

beforeEach(() => {
  vi.clearAllMocks();
});

// Explicit teardown (on top of RTL's automatic cleanup) plus a macrotask
// flush: guarantees the previous test's component is fully unmounted and any
// in-flight promise from it has had a chance to settle against a torn-down
// tree before the next test's render() starts, so nothing from one test's
// async work can bleed into the next test's timing or assertions.
afterEach(async () => {
  cleanup();
  await new Promise((resolve) => setTimeout(resolve, 0));
});

// Bundles the "fully loaded, nothing pending" check into one waitFor so it's
// evaluated atomically — checking loading indicators one at a time across
// separate awaits leaves a window where the UI can be mid-transition.
async function waitForSettled() {
  await waitFor(
    () => {
      expect(screen.queryByText(/Loading watchlist/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Scanning for meaningful changes/)).not.toBeInTheDocument();
      expect(screen.queryByText(/Loading your watchlists/)).not.toBeInTheDocument();
    },
    { timeout: 3000 }
  );
}

describe('App — core user journey', () => {
  it('renders watchlist data from the backend', async () => {
    setupDefaultMocks();
    render(<App />);

    await waitFor(
      () => {
        expect(screen.getByText('STK01')).toBeInTheDocument();
        expect(screen.getByText('FUND01')).toBeInTheDocument();
        expect(screen.getByText('Acme Corp')).toBeInTheDocument();
      },
      { timeout: 3000 }
    );
    await waitForSettled();
  });

  it('renders attention items when present', async () => {
    setupDefaultMocks({ attention: [ATTENTION_ITEM] });
    render(<App />);

    await waitFor(
      () => {
        expect(screen.getByTestId('attention-list')).toBeInTheDocument();
        expect(
          screen.getByText(/moved 9.1% versus its normal daily range/)
        ).toBeInTheDocument();
      },
      { timeout: 3000 }
    );
    await waitForSettled();
  });

  it('renders the empty attention state when nothing is meaningful', async () => {
    setupDefaultMocks({ attention: [] });
    render(<App />);

    // Both lines of the empty state are asserted inside the SAME waitFor so
    // they're checked together at one consistent point in time, rather than
    // across two separate awaited steps.
    await waitFor(
      () => {
        expect(screen.getByText('Nothing important changed')).toBeInTheDocument();
        expect(
          screen.getByText('Your watchlist looks normal based on its recent behavior.')
        ).toBeInTheDocument();
      },
      { timeout: 3000 }
    );
    expect(screen.queryByTestId('attention-list')).not.toBeInTheDocument();
    await waitForSettled();
  });

  it('shows a loading indicator before data arrives', async () => {
    setupDefaultMocks();
    let resolveWatchlist;
    api.getWatchlist.mockReturnValue(new Promise((res) => (resolveWatchlist = res)));

    render(<App />);
    await waitFor(
      () => expect(screen.getByText(/Loading watchlist/)).toBeInTheDocument(),
      { timeout: 3000 }
    );

    resolveWatchlist(WATCHLIST);
    await waitForSettled();
  });

  it('shows an error state when the backend call fails', async () => {
    api.listWatchlists.mockResolvedValue(SUMMARIES);
    api.getWatchlist.mockRejectedValue(new Error('Could not reach the backend.'));
    api.detectChanges.mockResolvedValue([]);
    api.getAttentionItems.mockResolvedValue([]);

    render(<App />);

    await waitFor(
      () => expect(screen.getByText('Could not reach the backend.')).toBeInTheDocument(),
      { timeout: 3000 }
    );
    await waitFor(() =>
      expect(screen.queryByText(/Scanning for meaningful changes/)).not.toBeInTheDocument()
    );
  });

  it('filters the table client-side by symbol, name, or sector', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.getByText('FUND01')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Search your watchlist'), 'Acme');

    await waitFor(() => {
      expect(screen.getByText('STK01')).toBeInTheDocument();
      expect(screen.queryByText('FUND01')).not.toBeInTheDocument();
    });
    await waitForSettled();
  });

  it('opens the add form and calls addItem with the entered symbol and type, then refreshes', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ add stocks/i }));

    await user.type(screen.getByLabelText('Symbol'), 'stk09');
    await user.selectOptions(screen.getByLabelText('Type'), 'STOCK');
    await user.click(screen.getByRole('button', { name: /add to watchlist/i }));

    await waitFor(() => expect(api.addItem).toHaveBeenCalledWith(1, 'stk09', 'STOCK'));
    await waitFor(() => expect(api.getWatchlist).toHaveBeenCalledTimes(2)); // initial + post-add refresh
    await waitForSettled();
  });

  it('only shows remove controls in edit mode, and calls removeItem with the correct symbol', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.queryByRole('button', { name: /remove/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    const removeButtons = await screen.findAllByRole('button', { name: /remove/i });
    await user.click(removeButtons[0]);

    await waitFor(() => expect(api.removeItem).toHaveBeenCalledWith(1, 'STK01'));
    await waitForSettled();
  });

  it('opens the naming dialog, lets the user rename, and creates+switches to it', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ watchlist/i }));

    const nameInput = await screen.findByLabelText(/watchlist name/i);
    expect(nameInput).toHaveValue('My Watchlist'); // sensible default, but editable
    await user.clear(nameInput);
    await user.type(nameInput, 'Tech Picks');
    await user.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(() => expect(api.createWatchlist).toHaveBeenCalledWith('Tech Picks'));
    await waitFor(() => expect(screen.getByText('New Watchlist')).toBeInTheDocument());
    expect(screen.queryByLabelText(/watchlist name/i)).not.toBeInTheDocument(); // dialog closes after success
    await waitForSettled();
  });

  it('creates nothing if watchlist creation is cancelled', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ watchlist/i }));
    await screen.findByLabelText(/watchlist name/i);

    await user.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByLabelText(/watchlist name/i)).not.toBeInTheDocument();
    expect(api.createWatchlist).not.toHaveBeenCalled();
    await waitForSettled();
  });

  it('rejects an empty/whitespace-only watchlist name without calling the API', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ watchlist/i }));

    const nameInput = await screen.findByLabelText(/watchlist name/i);
    await user.clear(nameInput);
    await user.type(nameInput, '   ');
    await user.click(screen.getByRole('button', { name: /^create$/i }));

    expect(await screen.findByText(/cannot be empty/i)).toBeInTheDocument();
    expect(api.createWatchlist).not.toHaveBeenCalled();
    await waitForSettled();
  });

  it('creating a second watchlist with a different name keeps both tabs independent', async () => {
    setupDefaultMocks();
    api.createWatchlist.mockResolvedValue({ id: 5, name: 'Dividend Picks', createdAt: '2026-08-24T00:00:00Z' });
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ watchlist/i }));
    const nameInput = await screen.findByLabelText(/watchlist name/i);
    await user.clear(nameInput);
    await user.type(nameInput, 'Dividend Picks');
    await user.click(screen.getByRole('button', { name: /^create$/i }));

    await waitFor(() => expect(screen.getByText('Dividend Picks')).toBeInTheDocument());
    expect(screen.getByText('My Watchlist')).toBeInTheDocument(); // original tab still there, untouched
    await waitForSettled();
  });

  it('calls checkWatchlist only when the check button is clicked, never on render', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    expect(api.checkWatchlist).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /check for changes/i }));
    await waitFor(() => expect(api.checkWatchlist).toHaveBeenCalledWith(1));
    await waitForSettled();
  });

  it('only shows "Delete watchlist" in edit mode', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    expect(screen.queryByRole('button', { name: /delete watchlist/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    expect(screen.getByRole('button', { name: /delete watchlist/i })).toBeInTheDocument();
    await waitForSettled();
  });

  it('does not delete the watchlist if the confirmation is declined', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await user.click(screen.getByRole('button', { name: /delete watchlist/i }));

    expect(confirmSpy).toHaveBeenCalled();
    expect(api.deleteWatchlist).not.toHaveBeenCalled();
    await waitForSettled();
    confirmSpy.mockRestore();
  });

  it('deletes the watchlist after confirmation and falls back to the empty state when it was the last one', async () => {
    setupDefaultMocks(); // single watchlist in SUMMARIES
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await user.click(screen.getByRole('button', { name: /delete watchlist/i }));

    await waitFor(() => expect(api.deleteWatchlist).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.getByText('No watchlist yet')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /create watchlist/i })).toBeInTheDocument();
    confirmSpy.mockRestore();
  });

  it('deletes the active watchlist and switches to another one when more than one exists', async () => {
    setupDefaultMocks({
      summaries: [
        { id: 1, name: 'My Watchlist', createdAt: '2026-08-24T10:00:00Z', itemCount: 2 },
        { id: 3, name: 'Second List', createdAt: '2026-08-24T10:00:00Z', itemCount: 0 },
      ],
    });
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await user.click(screen.getByRole('button', { name: /delete watchlist/i }));

    await waitFor(() => expect(api.deleteWatchlist).toHaveBeenCalledWith(1));
    // the deleted tab is gone and the remaining watchlist is now active
    await waitFor(() => expect(screen.queryByText('My Watchlist')).not.toBeInTheDocument());
    expect(screen.getByText('Second List')).toBeInTheDocument();
    confirmSpy.mockRestore();
  });

  it('shows an error message if deleting the watchlist fails', async () => {
    setupDefaultMocks();
    api.deleteWatchlist.mockRejectedValue(new Error('Could not delete watchlist.'));
    const user = userEvent.setup();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /^edit$/i }));
    await user.click(screen.getByRole('button', { name: /delete watchlist/i }));

    await waitFor(() =>
      expect(screen.getByText('Could not delete watchlist.')).toBeInTheDocument()
    );
    // the watchlist must NOT have been removed from the UI on failure
    expect(screen.getByText('My Watchlist')).toBeInTheDocument();
    confirmSpy.mockRestore();
  });
});

describe('App — global instrument search', () => {
  it('shows matching stock and fund results as the user types, without adding anything', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.type(screen.getByLabelText('Search stocks, funds'), 'STK');

    const listbox = await screen.findByRole('listbox');
    await waitFor(() => {
      expect(within(listbox).getByText('Acme Corp')).toBeInTheDocument();
      expect(within(listbox).getByText('Beta Industries')).toBeInTheDocument();
    });
    expect(within(listbox).queryByText('Growth Fund')).not.toBeInTheDocument(); // FUND01 doesn't match "STK"
    expect(api.addItem).not.toHaveBeenCalled();
    await waitForSettled();
  });

  it('labels results by instrument type so stocks and funds are distinguishable', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    // "o" matches both "Acme Corp" (STOCK) and "Growth Fund" (FUND)
    await user.type(screen.getByLabelText('Search stocks, funds'), 'o');

    const listbox = await screen.findByRole('listbox');
    const options = within(listbox).getAllByRole('option');
    expect(options.length).toBeGreaterThan(0);
    expect(
      within(listbox).getAllByText('STOCK').length + within(listbox).getAllByText('FUND').length
    ).toBe(options.length);
    await waitForSettled();
  });

  it('shows a clear "no match" state for a query that matches nothing', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.type(screen.getByLabelText('Search stocks, funds'), 'zzz-nope');

    await waitFor(() =>
      expect(screen.getByText('No matching stocks or funds')).toBeInTheDocument()
    );
    await waitForSettled();
  });

  it('clearing the search removes the results dropdown', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    const searchInput = screen.getByLabelText('Search stocks, funds');
    await user.type(searchInput, 'STK');
    await waitFor(() => expect(screen.getByRole('listbox')).toBeInTheDocument());

    await user.clear(searchInput);
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
    await waitForSettled();
  });

  it('selecting a result opens the add form prefilled with that symbol and type, without calling addItem', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.type(screen.getByLabelText('Search stocks, funds'), 'growth');

    const result = await screen.findByRole('option', { name: /growth fund/i });
    await user.click(result);

    expect(await screen.findByLabelText('Symbol')).toHaveValue('FUND01');
    expect(screen.getByLabelText('Type')).toHaveValue('FUND');
    expect(api.addItem).not.toHaveBeenCalled(); // selecting a result must not mutate the watchlist
    // the dropdown itself is gone after selecting
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    await waitForSettled();
  });

  it('keeps the global instrument search independent from "Search your watchlist"', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.type(screen.getByLabelText('Search stocks, funds'), 'growth');

    const listbox = await screen.findByRole('listbox');
    await waitFor(() => expect(within(listbox).getByText('Growth Fund')).toBeInTheDocument());
    // the watchlist table itself is untouched by the global search
    const table = screen.getByTestId('watchlist-table');
    expect(within(table).getByText('STK01')).toBeInTheDocument();
    expect(within(table).getByText('FUND01')).toBeInTheDocument();
    await waitForSettled();
  });
});

describe('App — stale watchlist recovery (Issue 3)', () => {
  it('re-syncs the watchlist list and drops the stale selection when adding fails with a 404', async () => {
    setupDefaultMocks();
    const staleError = new Error('Watchlist not found: 1');
    staleError.status = 404;
    api.addItem.mockRejectedValueOnce(staleError);
    // the recovery re-fetch returns a list that no longer contains id 1
    api.listWatchlists.mockResolvedValueOnce(SUMMARIES).mockResolvedValueOnce([
      { id: 3, name: 'Second List', createdAt: '2026-08-24T10:00:00Z', itemCount: 0 },
    ]);

    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ add stocks/i }));
    await user.type(screen.getByLabelText('Symbol'), 'stk09');
    await user.click(screen.getByRole('button', { name: /add to watchlist/i }));

    await waitFor(() => expect(api.listWatchlists).toHaveBeenCalledTimes(2)); // initial load + recovery re-fetch
    await waitFor(() => expect(screen.queryByText('My Watchlist')).not.toBeInTheDocument());
    expect(screen.getByText('Second List')).toBeInTheDocument();
  });

  it('does not re-sync the watchlist list for a non-404 add failure', async () => {
    setupDefaultMocks();
    api.addItem.mockRejectedValueOnce(new Error('Could not reach the backend.'));

    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ add stocks/i }));
    await user.type(screen.getByLabelText('Symbol'), 'stk09');
    await user.click(screen.getByRole('button', { name: /add to watchlist/i }));

    await waitFor(() => expect(screen.getByText('Could not reach the backend.')).toBeInTheDocument());
    expect(api.listWatchlists).toHaveBeenCalledTimes(1); // no recovery re-fetch for a plain network error
  });
});
