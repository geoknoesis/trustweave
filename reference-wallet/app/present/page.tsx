'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { bootstrap, createPresentation, list, type StoredCredential, type WalletState } from '@/lib/wallet'

interface VerificationCheck {
  step: string
  passed: boolean
  detail?: string
}

interface VerificationResponse {
  valid: boolean
  checks: VerificationCheck[]
  holder?: string
  credentials?: Array<{
    type: string[]
    issuer: string
    subject: string
    disclosedClaims: Record<string, unknown>
    withheldClaimNames?: string[]
  }>
}

type Status =
  | { kind: 'idle' }
  | { kind: 'fetching-request' }
  | { kind: 'building-vp' }
  | { kind: 'verifying' }
  | { kind: 'done'; response: VerificationResponse }
  | { kind: 'error'; message: string }

export default function PresentPage() {
  const [state, setState] = useState<WalletState | null>(null)
  const [credentials, setCredentials] = useState<StoredCredential[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [disclose, setDisclose] = useState<Record<string, boolean>>({})
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  useEffect(() => {
    const initial = bootstrap()
    setState(initial)
    const creds = list()
    setCredentials(creds)
    if (creds.length > 0) setSelectedId(creds[0].id)
  }, [])

  // Reset disclosure selection when the chosen credential changes.
  useEffect(() => {
    if (!selectedId) return
    const cred = credentials.find((c) => c.id === selectedId)
    if (!cred) return
    const initial: Record<string, boolean> = {}
    for (const name of cred.selectivelyDisclosable) initial[name] = true  // default: reveal all
    setDisclose(initial)
  }, [selectedId, credentials])

  if (!state) {
    return (
      <div className="panel">
        <div className="status-text loading">Initialising wallet…</div>
      </div>
    )
  }

  const onPresent = async () => {
    if (!selectedId) return
    try {
      setStatus({ kind: 'fetching-request' })
      const reqRes = await fetch('/api/demo-verifier/request')
      if (!reqRes.ok) throw new Error(`Verifier request failed: HTTP ${reqRes.status}`)
      const presentationRequest = (await reqRes.json()) as {
        verifier: string
        audience: string
        nonce: string
        acceptedTypes: string[]
      }

      setStatus({ kind: 'building-vp' })
      const discloseNames = Object.entries(disclose).filter(([, v]) => v).map(([k]) => k)
      const vp = createPresentation(
        [selectedId],
        presentationRequest.audience,
        presentationRequest.nonce,
        discloseNames,
      )

      setStatus({ kind: 'verifying' })
      const selected = credentials.find((c) => c.id === selectedId)!
      const verifyRes = await fetch('/api/demo-verifier/verify', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          presentation: vp,
          format: selected.format,
          expectedNonce: presentationRequest.nonce,
        }),
      })
      if (!verifyRes.ok) throw new Error(`Verify failed: HTTP ${verifyRes.status}`)
      const response = (await verifyRes.json()) as VerificationResponse

      setStatus({ kind: 'done', response })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  if (credentials.length === 0) {
    return (
      <div className="panel">
        <h2>Present a credential</h2>
        <div className="empty">
          <div className="icon">📭</div>
          <div>You have no credentials to present.</div>
          <div style={{ marginTop: '1rem' }}>
            <Link href="/receive" className="btn">
              Receive one first
            </Link>
          </div>
        </div>
      </div>
    )
  }

  const selectedCred = credentials.find((c) => c.id === selectedId)
  const disclosableNames = selectedCred?.selectivelyDisclosable ?? []
  const isSdJwt = selectedCred?.format === 'vc+sd-jwt'

  return (
    <>
      <div className="panel">
        <h2>Present a credential</h2>
        <p style={{ color: 'var(--text-muted)' }}>
          The wallet will build a Verifiable Presentation containing the selected credential, signed by
          your holder DID, and send it to the demo verifier. The verifier&apos;s checklist appears below.
        </p>

        <h3>Choose a credential</h3>
        {credentials.map((c) => (
          <label
            key={c.id}
            className="credential-card"
            style={{ cursor: 'pointer', border: selectedId === c.id ? '2px solid var(--primary)' : undefined }}
          >
            <input
              type="radio"
              name="cred"
              checked={selectedId === c.id}
              onChange={() => setSelectedId(c.id)}
              style={{ marginRight: '0.5rem' }}
            />
            <div className="icon">🎓</div>
            <div className="body">
              <div className="title">{c.preview.title} <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>({c.format})</span></div>
              {c.preview.subtitle && <div className="subtitle">{c.preview.subtitle}</div>}
            </div>
          </label>
        ))}

        {isSdJwt && disclosableNames.length > 0 && (
          <>
            <h3>Disclose to verifier</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>
              Tick the claims you&apos;re willing to reveal. Unticked claims stay hashed inside the
              credential — the verifier sees only that the issuer signed something, not what.
            </p>
            <div style={{ background: 'var(--surface-alt)', borderRadius: '6px', padding: '0.85rem 1rem' }}>
              {disclosableNames.map((name) => (
                <label key={name} style={{ display: 'flex', alignItems: 'center', padding: '0.3rem 0', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={disclose[name] ?? false}
                    onChange={(e) => setDisclose({ ...disclose, [name]: e.target.checked })}
                    style={{ marginRight: '0.6rem' }}
                  />
                  <code style={{ color: disclose[name] ? 'var(--success)' : 'var(--text-muted)' }}>{name}</code>
                </label>
              ))}
            </div>
          </>
        )}

        <div className="button-row">
          <button onClick={onPresent} disabled={!selectedId || status.kind === 'fetching-request' || status.kind === 'building-vp' || status.kind === 'verifying'}>
            {status.kind === 'idle' || status.kind === 'done' || status.kind === 'error' ? 'Present to demo verifier' : statusLabel(status)}
          </button>
        </div>

        {(status.kind === 'fetching-request' || status.kind === 'building-vp' || status.kind === 'verifying') && (
          <div className="status-text loading">{statusLabel(status)}</div>
        )}

        {status.kind === 'error' && (
          <div className="callout danger">
            <strong>Failed:</strong> {status.message}
          </div>
        )}
      </div>

      {status.kind === 'done' && (
        <div className="panel">
          <h2>Verification result</h2>
          <div className={`callout ${status.response.valid ? 'success' : 'danger'}`}>
            <strong>{status.response.valid ? '✓ Presentation verified.' : '✗ Verification failed.'}</strong>
          </div>

          <h3>Checklist</h3>
          <ul className="check-list">
            {status.response.checks.map((c, i) => (
              <li key={i} className={c.passed ? 'pass' : 'fail'}>
                <div className="marker">{c.passed ? '✓' : '✗'}</div>
                <div>
                  <div>{c.step}</div>
                  {c.detail && <div className="detail">{c.detail}</div>}
                </div>
              </li>
            ))}
          </ul>

          {status.response.credentials && status.response.credentials.length > 0 && (
            <>
              <h3>What the verifier saw</h3>
              {status.response.credentials.map((cred, i) => (
                <div key={i} className="disclosure-block">
                  <div style={{ marginBottom: '0.5rem' }}>
                    <span className="claim-key">type:</span>{' '}
                    <span className="claim-value">{cred.type.join(', ')}</span>
                  </div>
                  {Object.entries(cred.disclosedClaims).map(([k, v]) => (
                    <div key={k}>
                      <span className="claim-key">{k}:</span>{' '}
                      <span className="claim-value">
                        {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                      </span>
                    </div>
                  ))}
                  {cred.withheldClaimNames && cred.withheldClaimNames.length > 0 && (
                    <div style={{ marginTop: '0.75rem', paddingTop: '0.5rem', borderTop: '1px dashed var(--border)' }}>
                      <span className="claim-key" style={{ color: 'var(--text-muted)' }}>withheld:</span>{' '}
                      <span style={{ color: 'var(--text-muted)' }}>{cred.withheldClaimNames.join(', ')}</span>
                    </div>
                  )}
                </div>
              ))}
            </>
          )}
        </div>
      )}
    </>
  )
}

function statusLabel(s: Status): string {
  switch (s.kind) {
    case 'fetching-request': return 'Fetching verifier request…'
    case 'building-vp': return 'Building presentation…'
    case 'verifying': return 'Verifying…'
    default: return ''
  }
}
