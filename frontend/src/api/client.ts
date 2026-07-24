export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  timestamp: string
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
  ) {
    super(message)
  }
}

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(payload?.code ?? 'NETWORK_ERROR', payload?.message ?? '请求失败', response.status)
  }
  return (payload as ApiEnvelope<T>).data
}

export async function apiDownload(path: string, token: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(payload?.code ?? 'DOWNLOAD_FAILED', payload?.message ?? '下载失败', response.status)
  }
  return response.blob()
}
