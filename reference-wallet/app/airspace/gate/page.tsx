'use client'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import { VerifierRequestQr } from '@/components/VerifierRequestQr'
import { humanClaimName } from '@/lib/credential-display'
import type { PresentationRequestParams } from '@/lib/presentation-request-qr'

interface GateVerification {
  valid: boolean
  checks: Array<{ step: string; passed: boolean; detail?: string }>
  holder?: string
  gate?: { activityType: string; lat: number; lon: number; domainId: string }
  credentials?: Array<{
    disclosedClaims: Record<string, unknown>
  }>
}

const GATE_REQUEST_ENDPOINT = '/api/demo-airspace/gate/request'
const GATE_VERIFY_ENDPOINT = '/api/demo-airspace/gate/verify'

export default function AirspaceGatePage() {
  const [gateUrl, setGateUrl] = useState('')
  const [urlHint, setUrlHint] = useState<string | null>(null)
  const [activityType, setActivityType] = useState('data-collection')
  const [lat, setLat] = useState('37.7749')
  const [lon, setLon] = useState('-122.4194')
  const [request, setRequest] = useState<PresentationRequestParams | null>(null)
  const [latest, setLatest] = useState<GateVerification | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    fetch('/api/demo-issuer/server-url')
      .then((r) => r.json())
      .then((data: { origin: string; mobileReachableUrl: string | null; hint: string | null }) => {
        setGateUrl(data.mobileReachableUrl ?? data.origin)
        if (data.hint) setUrlHint(data.hint)
      })
      .catch(() => {
        if (typeof window !== 'undefined') setGateUrl(window.location.origin)
      })
  }, [])

  const openGate = useCallback(async () => {
    setRefreshing(true)
    setLoadError(null)
    setLatest(null)
    try {
      const res = await fetch(GATE_REQUEST_ENDPOINT, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          activityType,
          lat: Number(lat),
          lon: Number(lon),
        }),
      })
      if (!res.ok) throw new Error(`Gate request HTTP ${res.status}`)
      const data = (await res.json()) as PresentationRequestParams
      setRequest(data)
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e))
    } finally {
      setRefreshing(false)
    }
  }, [activityType, lat, lon])

  useEffect(() => {
    if (!request?.nonce) return
    let cancelled = false
    const poll = async () => {
      try {
        const res = await fetch(
          `/api/demo-airspace/gate/recent?nonce=${encodeURIComponent(request.nonce)}`,
        )
        if (!res.ok) return
        const data = (await res.json()) as { latest: GateVerification | null }
        if (!cancelled && data.latest) setLatest(data.latest)
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
        <h2>Airspace gatekeeper</h2>
        <p>
          Verify drone activity authorization before flight. Drones present their
          <code> ActivityAuthorizationCredential</code> from the wallet Share tab.
        </p>
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Gate parameters</h3>
        <div className="form-grid">
          <label>
            <div className="label">Activity type</div>
            <select value={activityType} onChange={(e) => setActivityType(e.target.value)}>
              <option value="data-collection">data-collection</option>
              <option value="monitoring">monitoring</option>
              <option value="inspection">inspection</option>
            </select>
          </label>
          <label>
            <div className="label">Latitude</div>
            <input value={lat} onChange={(e) => setLat(e.target.value)} />
          </label>
          <label>
            <div className="label">Longitude</div>
            <input value={lon} onChange={(e) => setLon(e.target.value)} />
          </label>
        </div>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
          Default coordinates are downtown San Francisco — inside the SF Bay demo domain.
          Try <code>34.0522</code>, <code>-118.2437</code> (Los Angeles) to simulate denial.
        </p>
        <button type="button" className="btn" onClick={openGate} disabled={refreshing}>
          {refreshing ? 'Opening gate…' : 'Open gate & show QR'}
        </button>
        {loadError && (
          <div className="callout danger" style={{ marginTop: '0.75rem' }}>{loadError}</div>
        )}
      </div>

      {request && gateUrl && (
        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Presentation request QR</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            Scan from the wallet <Link href="/present">Share</Link> tab. Nonce:{' '}
            <code>{request.nonce.slice(0, 8)}…</code>
          </p>
          {urlHint && (
            <div className="callout warning" style={{ fontSize: '0.85rem', marginBottom: '0.75rem' }}>{urlHint}</div>
          )}
          <label style={{ display: 'block', marginBottom: '1rem' }}>
            <div className="label">Gate URL in QR (LAN for phone)</div>
            <input
              value={gateUrl}
              onChange={(e) => setGateUrl(e.target.value)}
              style={{
                width: '100%',
                padding: '0.5rem',
                borderRadius: 6,
                border: '1px solid var(--border)',
                fontFamily: 'monospace',
                fontSize: '0.85rem',
              }}
            />
          </label>
          <VerifierRequestQr
            verifierUrl={gateUrl}
            presentationRequest={request}
            requestEndpoint={GATE_REQUEST_ENDPOINT}
            verifyEndpoint={GATE_VERIFY_ENDPOINT}
            size={220}
          />
        </div>
      )}

      {latest && (
        <div className={`panel ${latest.valid ? 'callout success' : 'callout danger'}`}>
          <h3 style={{ marginTop: 0 }}>
            {latest.valid ? '✅ Cleared for activity' : '❌ Access denied'}
          </h3>
          {latest.holder && (
            <div className="identity-card">
              <div className="label">Drone agent DID</div>
              <div className="identity-value">{latest.holder}</div>
            </div>
          )}
          <ul className="check-list" style={{ marginTop: '1rem' }}>
            {latest.checks.map((c) => (
              <li key={c.step} className={c.passed ? 'pass' : 'fail'}>
                <strong>{c.step}</strong>
                {c.detail ? ` — ${c.detail}` : ''}
              </li>
            ))}
          </ul>
          {latest.credentials?.[0]?.disclosedClaims && (
            <div style={{ marginTop: '1rem' }}>
              <div className="label">Disclosed authorization claims</div>
              <dl className="detail-grid">
                {Object.entries(latest.credentials[0].disclosedClaims).map(([k, v]) => (
                  <div key={k}>
                    <dt>{humanClaimName(k)}</dt>
                    <dd>{String(v)}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Demo flow</h3>
        <ol className="step-list">
          <li>Open <Link href="/issuer/faa/DRONE-001">FAA identification</Link> and scan the offer QR (Receive tab).</li>
          <li>Open <Link href="/issuer/airspace/DRONE-001">airspace authorization</Link> and scan that offer QR too.</li>
          <li>Set gate parameters above and click <strong>Open gate & show QR</strong>.</li>
          <li>Share tab → scan gate QR → disclose activity claims → Share with verifier.</li>
          <li>Gate status updates here when the presentation is verified.</li>
        </ol>
      </div>
    </>
  )
}
