import { API_BASE_URL } from '../config.js';

// A backend error body looks like:
// { timestamp, status, error, message } (see GlobalExceptionHandler.java)
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function buildUrl(path, params) {
  const url = new URL(path, API_BASE_URL);
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      url.searchParams.set(key, value);
    }
  });
  return url.toString();
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request(path, { method = 'GET', params, body } = {}) {
  let response;
  try {
    response = await fetch(buildUrl(path, params), {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (networkErr) {
    // fetch itself throws on network failure (backend down, CORS, offline)
    throw new ApiError(
      'Could not reach the backend. Is it running at ' + API_BASE_URL + '?',
      0
    );
  }

  const data = await parseBody(response);

  if (!response.ok) {
    const message =
      (data && typeof data === 'object' && data.message) ||
      `Request failed (${response.status})`;
    throw new ApiError(message, response.status);
  }

  return data;
}

export const apiClient = {
  get: (path, params) => request(path, { method: 'GET', params }),
  post: (path, params, body) => request(path, { method: 'POST', params, body }),
  delete: (path, params) => request(path, { method: 'DELETE', params }),
};
