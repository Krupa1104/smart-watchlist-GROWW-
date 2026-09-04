import { render, screen, waitFor, cleanup } from '@testing-library/react';
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

function setupDefaultMocks({ attention = [], detected = DETECTED, summaries = SUMMARIES } = {}) {
  api.listWatchlists.mockResolvedValue(summaries);
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

  it('creates a new watchlist and switches to it', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await waitFor(() => expect(screen.getByText('STK01')).toBeInTheDocument(), { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /\+ watchlist/i }));

    await waitFor(() => expect(api.createWatchlist).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByText('New Watchlist')).toBeInTheDocument());
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
