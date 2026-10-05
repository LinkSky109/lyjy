import type { AuthState, Result } from './types';

const STORAGE_KEY = 'lhjypt.auth';

export function getStoredAuth(): AuthState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthState) : null;
  } catch {
    return null;
  }
}

export function storeAuth(auth: AuthState): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
}

export function clearStoredAuth(): void {
  localStorage.removeItem(STORAGE_KEY);
}

function authHeaders(): Record<string, string> {
  const auth = getStoredAuth() ?? { role: 'VIEWER', userId: 'viewer', group: '' };
  const headers: Record<string, string> = {
    'X-User-Role': auth.role,
    'X-User-Id': auth.userId || 'anonymous',
  };
  if (auth.group) {
    headers['X-User-Group'] = auth.group;
  }
  return headers;
}

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

function buildQuery(params?: Record<string, unknown>): string {
  if (!params) {
    return '';
  }
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    search.append(key, String(value));
  });
  const query = search.toString();
  return query ? `?${query}` : '';
}

interface RequestOptions {
  params?: Record<string, unknown>;
  body?: unknown;
  token?: string;
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { ...authHeaders() };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.token) {
    headers['X-Confirm-Token'] = options.token;
  }

  const response = await fetch(path + buildQuery(options.params), {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  const text = await response.text();
  let payload: unknown = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }

  if (!response.ok) {
    const body = payload as Partial<Result<unknown>> | null;
    throw new ApiError(body?.code ?? response.status, body?.message ?? response.statusText);
  }

  if (payload && typeof payload === 'object' && 'code' in payload) {
    const envelope = payload as Result<T>;
    if (envelope.code !== 0) {
      throw new ApiError(envelope.code, envelope.message);
    }
    return envelope.data;
  }
  return payload as T;
}

export const api = {
  get: <T>(path: string, params?: Record<string, unknown>) => request<T>('GET', path, { params }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('POST', path, { body, ...options }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>('PUT', path, { body, ...options }),
  del: <T>(path: string, params?: Record<string, unknown>) => request<T>('DELETE', path, { params }),
  /** 签发一次性二次确认令牌（30s） */
  confirmToken: () => request<{ token: string }>('POST', '/api/auth/confirm-token'),
};

export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    return `[${error.code}] ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}
