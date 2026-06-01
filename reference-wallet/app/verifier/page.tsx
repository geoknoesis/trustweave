'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { VerifierRequestQr } from '@/components/VerifierRequestQr'
import { PortraitFrame } from '@/components/PortraitFrame'
import { humanClaimName } from '@/lib/credential-display'
import { portraitSrcFromClaims } from '@/lib/portrait-display'
import {
  buildPresentationRequestQrPayload,
  buildPresentationRequestUrl,
  type PresentationRequestParams,
} from '@/lib/presentation-request-qr'

interface StoredVerification {
  id: string
  at: string
  valid: boolean
  nonce?: string
  holder?: string
  checks: Array<{ step: string; passed: boolean; detail?: string }>
  credentials?: Array<{
    type: string[]
    issuer: string
    subject: string
    disclosedClaims: Record<string, unknown>
  }>
}

interface RecentResponse {
  latest: StoredVerification | null
  recent: StoredVerification[]
}

export default function VerifierPage() {
  const [verifierUrl, setVerifierUrl] = useState('')
  const [urlHint, setUrlHint] = useState<string | null>(null)
  const [request, setRequest] = useState<PresentationRequestParams | null>(null)
  const [latest, setLatest] = useState<StoredVerification | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  /** Browser API calls always use same-origin; [verifierUrl] is only for the phone QR. */
  const loadSession = useCallback(async () => {
    const res = await fetch('/api/demo-verifier/request')
    if (!res.ok) throw new Error(`Verifier request HTTP ${res.status}`)
    return (await res.json()) as PresentationRequestParams
  }, [])

  const refreshQr = useCallback(async () => {
    setRefreshing(true)
    setLoadError(null)
    setLatest(null)
    try {
      setRequest(await loadSession())
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e))
    } finally {
      setRefreshing(false)
    }
  }, [loadSession])

  useEffect(() => {
    fetch('/api/demo-issuer/server-url')
      .then((r) => r.json())
      .then(async (data: { origin: string; mobileReachableUrl: string | null; hint: string | null }) => {
        const url = data.mobileReachableUrl ?? data.origin
        setVerifierUrl(url)
        if (data.hint) setUrlHint(data.hint)
        setLatest(null)
        setRequest(await loadSession())
      })
      .catch((e) => {
        setLoadError(e instanceof Error ? e.message : String(e))
        if (typeof window !== 'undefined') setVerifierUrl(window.location.origin)
      })
  }, [loadSession])

  useEffect(() => {
    if (!request?.nonce) return
    let cancelled = false
    const poll = async () => {
      try {
        const res = await fetch(
          `/api/demo-verifier/recent?nonce=${encodeURIComponent(request.nonce)}`,
        )
        if (!res.ok) return
        const data = (await res.json()) as RecentResponse
        if (!cancelled) setLatest(data.latest)
      } catch {
        // ignore poll errors
      }
    }
    poll()
    const id = setInterval(poll, 2000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [request?.nonce])

  return (
    <>
      <div className="page-hero">
        <h2>Verifier</h2>
        <p>Show this QR so the holder can scan it from their wallet&apos;s Share tab.</p>
      </div>

      <div className="panel">
        <label style={{ display: 'block', marginBottom: '1rem' }}>
          <div className="label" style={{ marginBottom: '0.35rem' }}>Verifier URL in QR (must reach phone on LAN)</div>
          <input
            value={verifierUrl}
            onChange={(e) => setVerifierUrl(e.target.value)}
            style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)', fontFamily: 'monospace', fontSize: '0.85rem' }}
          />
          {urlHint && (
            <div className="callout warning" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>{urlHint}</div>
          )}
        </label>

        {loadError && (
          <div className="callout danger" style={{ marginBottom: '1rem' }}>
            <strong>Could not start verifier session</strong>
            <div style={{ marginTop: '0.25rem' }}>{loadError}</div>
          </div>
        )}

        {verifierUrl && request && (
          <>
            <div style={{ textAlign: 'center', margin: '1rem 0' }}>
              <VerifierRequestQr verifierUrl={verifierUrl} presentationRequest={request} />
            </div>
            <div className="button-row" style={{ justifyContent: 'center', marginBottom: '1rem' }}>
              <button type="button" className="secondary" onClick={refreshQr} disabled={refreshing}>
                {refreshing ? 'Refreshing…' : 'New QR session'}
              </button>
            </div>
            <div className="callout info">
              <strong>Accepts credentials:</strong>{' '}
              {request.acceptedTypes.join(', ') || 'any credential'}
            </div>
            <div className="identity-card">
              <div className="label">Verifier DID (KB-JWT audience)</div>
              <div className="identity-value">{request.verifier}</div>
            </div>
            <div className="identity-card" style={{ marginTop: '0.75rem' }}>
              <div className="label">Session nonce</div>
              <div className="identity-value">{request.nonce}</div>
            </div>
          </>
        )}

        {!request && verifierUrl && !loadError && (
          <div className="status-text loading">Starting verifier session…</div>
        )}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Verification status</h3>
        {!latest && (
          <div className="callout info">
            <strong>Waiting for this QR session</strong>
            <div style={{ marginTop: '0.35rem', fontSize: '0.9rem' }}>
              Scanning alone does not verify anything. The holder must tap{' '}
              <strong>Share with verifier</strong> on their phone — then this panel updates within a few seconds.
            </div>
            {request && (
              <div style={{ marginTop: '0.35rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                Watching session nonce <code>{request.nonce.slice(0, 8)}…</code>
              </div>
            )}
          </div>
        )}
        {latest && request && latest.nonce !== request.nonce && (
          <div className="callout warning" style={{ marginBottom: '0.75rem' }}>
            Showing a result from a previous session. Click <strong>New QR session</strong> and have the holder
            re-scan, then share again.
          </div>
        )}
        {latest && (
          <>
            <div className={`callout ${latest.valid ? 'success' : 'danger'}`}>
              <strong>
                {latest.valid ? '✓ Credential verified' : '✗ Verification failed'}
              </strong>
              <div style={{ marginTop: '0.35rem', fontSize: '0.85rem' }}>
                {new Date(latest.at).toLocaleString()}
                {latest.holder ? ` · holder ${latest.holder.slice(0, 28)}…` : ''}
              </div>
            </div>
            <ul className="check-list">
              {latest.checks.map((c, i) => (
                <li key={i} className={c.passed ? 'pass' : 'fail'}>
                  <div className="marker">{c.passed ? '✓' : '✗'}</div>
                  <div>
                    <div>{c.step}</div>
                    {c.detail && <div className="detail">{c.detail}</div>}
                  </div>
                </li>
              ))}
            </ul>
            {latest.credentials?.map((cred, i) => {
              const portraitSrc = portraitSrcFromClaims(cred.disclosedClaims)
              return (
              <div key={i} className="disclosure-block">
                {portraitSrc && (
                  <PortraitFrame
                    src={portraitSrc}
                    alt="Disclosed portrait"
                    caption="Portrait disclosed by holder"
                  />
                )}
                <div className="label">Disclosed claims</div>
                {Object.entries(cred.disclosedClaims).map(([k, v]) => (
                  <div key={k}>
                    <span className="claim-key">{humanClaimName(k)}:</span>{' '}
                    {k === 'portraitUrl' || k === 'portraitDigest'
                      ? String(v).slice(0, 48) + (String(v).length > 48 ? '…' : '')
                      : typeof v === 'object' ? JSON.stringify(v) : String(v)}
                  </div>
                ))}
              </div>
            )})}
          </>
        )}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>How it works</h3>
        <ol className="step-list">
          <li>Holder opens wallet → <Link href="/present">Share</Link> tab.</li>
          <li>They tap <strong>Scan verifier QR</strong> and scan this code.</li>
          <li>They choose what to disclose and tap <strong>Share with verifier</strong>.</li>
          <li>The result appears in <strong>Verification status</strong> above.</li>
        </ol>
        <p style={{ marginTop: '0.75rem' }}>
          <Link href="/verifier/test" className="text-link">Run automated verifier test →</Link>
          {' '}without a phone.
        </p>
        {verifierUrl && request && (
          <div className="identity-card" style={{ marginTop: '0.75rem' }}>
            <div className="label">Alternative URL (opens in wallet browser)</div>
            {buildPresentationRequestUrl(verifierUrl)}
          </div>
        )}
      </div>
    </>
  )
}
