import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
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

const ATTENTION_ITEM = {
  symbol: 'STK01',
  instrumentType: 'STOCK',
  asOfDate: '2026-08-24',
  changeType: 'PRICE_SPIKE',
  severity: 3.2,
  explanation: 'STK01 moved 9.1% versus its normal daily range.',
  metrics: { returnPct: 9.1, zScore: 3.2 },
};

function setupDefaultMocks({ attention = [] } = {}) {
  api.listWatchlists.mockResolvedValue(SUMMARIES);
  api.getWatchlist.mockResolvedValue(WATCHLIST);
  api.getAttentionItems.mockResolvedValue(attention);
  api.checkWatchlist.mockResolvedValue([]);
  api.addItem.mockResolvedValue({});
  api.removeItem.mockResolvedValue(undefined);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('App — core user journey', () => {
  it('renders watchlist data from the backend', async () => {
    setupDefaultMocks();
    render(<App />);

    expect(await screen.findByText('STK01')).toBeInTheDocument();
    expect(screen.getByText('FUND01')).toBeInTheDocument();
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
  });

  it('renders attention items when present', async () => {
    setupDefaultMocks({ attention: [ATTENTION_ITEM] });
    render(<App />);

    const list = await screen.findByTestId('attention-list');
    expect(list).toBeInTheDocument();
    expect(screen.getByText(/moved 9.1% versus its normal daily range/)).toBeInTheDocument();
  });

  it('renders the empty attention state when nothing is meaningful', async () => {
    setupDefaultMocks({ attention: [] });
    render(<App />);

    expect(await screen.findByText('Nothing important changed')).toBeInTheDocument();
    expect(screen.queryByTestId('attention-list')).not.toBeInTheDocument();
  });

  it('shows a loading indicator before data arrives', async () => {
    setupDefaultMocks();
    let resolveWatchlist;
    api.getWatchlist.mockReturnValue(new Promise((res) => (resolveWatchlist = res)));

    render(<App />);
    expect(await screen.findByText(/Loading watchlist/)).toBeInTheDocument();

    resolveWatchlist(WATCHLIST);
    await waitFor(() => expect(screen.queryByText(/Loading watchlist/)).not.toBeInTheDocument());
  });

  it('shows an error state when the backend call fails', async () => {
    api.listWatchlists.mockResolvedValue(SUMMARIES);
    api.getWatchlist.mockRejectedValue(new Error('Could not reach the backend.'));
    api.getAttentionItems.mockResolvedValue([]);

    render(<App />);

    expect(await screen.findByText('Could not reach the backend.')).toBeInTheDocument();
  });

  it('calls addItem with the entered symbol and type, then refreshes', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('STK01');

    await user.type(screen.getByLabelText('Symbol'), 'stk09');
    await user.selectOptions(screen.getByLabelText('Type'), 'STOCK');
    await user.click(screen.getByRole('button', { name: /add to watchlist/i }));

    await waitFor(() =>
      expect(api.addItem).toHaveBeenCalledWith(1, 'stk09', 'STOCK')
    );
    expect(api.getWatchlist).toHaveBeenCalledTimes(2); // initial + post-add refresh
  });

  it('calls removeItem with the correct symbol, then refreshes', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('STK01');
    const removeButtons = screen.getAllByRole('button', { name: /remove/i });
    await user.click(removeButtons[0]);

    await waitFor(() => expect(api.removeItem).toHaveBeenCalledWith(1, 'STK01'));
  });

  it('calls checkWatchlist (not on render) only when the check button is clicked', async () => {
    setupDefaultMocks();
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('STK01');
    expect(api.checkWatchlist).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /check for changes/i }));
    await waitFor(() => expect(api.checkWatchlist).toHaveBeenCalledWith(1));
  });
});
