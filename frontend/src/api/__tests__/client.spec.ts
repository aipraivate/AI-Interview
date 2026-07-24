import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiDownload, apiRequest } from '../client'

afterEach(() => vi.unstubAllGlobals())

describe('API transport', () => {
  it('unwraps successful envelopes and sends bearer tokens', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      code: 'OK', message: 'ok', data: { id: 'session-1' }, timestamp: new Date().toISOString(),
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest<{ id: string }>('/api/v1/test', {}, 'token-1'))
      .resolves.toEqual({ id: 'session-1' })
    const headers = fetchMock.mock.calls[0]![1]!.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer token-1')
  })

  it('does not override multipart boundaries and returns stable errors', async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OK', data: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'FILE_TOO_LARGE', message: '文件过大' }), {
        status: 413, headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)
    const form = new FormData()
    form.append('file', new Blob(['resume']), 'resume.pdf')
    await apiRequest('/upload', { method: 'POST', body: form }, 'token-1')
    expect((fetchMock.mock.calls[0]![1]!.headers as Headers).has('Content-Type')).toBe(false)
    await expect(apiRequest('/upload', { method: 'POST' }, 'token-1'))
      .rejects.toMatchObject({ code: 'FILE_TOO_LARGE', status: 413 } satisfies Partial<ApiError>)
  })

  it('downloads the completed privacy export', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>()
      .mockResolvedValue(new Response('{"account":[]}', { status: 200 })))
    const result = await apiDownload('/api/v1/privacy/requests/id/download', 'token-1')
    await expect(result.text()).resolves.toContain('account')
  })
})
