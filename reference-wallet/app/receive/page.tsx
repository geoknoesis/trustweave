'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { bootstrap, store, type WalletState } from '@/lib/wallet'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'success'; credentialId: string; type: string[]; subtitle?: string }
  | { kind: 'error'; message: string }

export default function ReceivePage() {
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  useEffect(() => {
    setState(bootstrap())
  }, [])

  if (!state) {
    return (
      <div className="panel">
        <div className="status-text loading">Initialising wallet…</div>
      </div>
    )
  }

  const onReceive = async () => {
    setStatus({ kind: 'requesting' })
    try {
      const response = await fetch(
        `/api/demo-issuer/credential?subject=${encodeURIComponent(state.holder.did)}`,
      )
      if (!response.ok) {
        const err = await response.json().catch(() => ({ error: 'request failed' }))
        throw new Error(err.error ?? `HTTP ${response.status}`)
      }
      const body = (await response.json()) as { credential: string; issuer: string; format: string }
      const stored = store(body.credential)
      setStatus({
        kind: 'success',
        credentialId: stored.id,
        type: stored.type,
        subtitle: stored.preview.subtitle,
      })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  return (
    <>
      <div className="panel">
        <h2>Receive a credential</h2>
        <p style={{ color: 'var(--text-muted)' }}>
          The demo issuer will sign a Bachelor of Science credential addressed to your holder DID
          and return it as a Verifiable Credential JWT. Your wallet stores it locally.
        </p>

        <div className="identity-card">
          <div className="label">Subject DID (you)</div>
          {state.holder.did}
        </div>

        <button onClick={onReceive} disabled={status.kind === 'requesting'}>
          {status.kind === 'requesting' ? 'Requesting…' : 'Receive demo credential'}
        </button>

        {status.kind === 'requesting' && (
          <div className="status-text loading">Calling demo issuer…</div>
        )}

        {status.kind === 'success' && (
          <div className="callout success">
            <strong>Received and stored.</strong>
            <div>{status.type.filter((t) => t !== 'VerifiableCredential').join(' · ') || 'VerifiableCredential'}</div>
            {status.subtitle && <div>{status.subtitle}</div>}
            <div style={{ marginTop: '0.75rem' }}>
              <Link href="/" className="btn">
                View in wallet
              </Link>
              {' '}
              <Link href="/present" className="btn secondary">
                Present it now
              </Link>
            </div>
          </div>
        )}

        {status.kind === 'error' && (
          <div className="callout danger">
            <strong>Failed:</strong> {status.message}
          </div>
        )}
      </div>

      <div className="panel">
        <h3>What just happened?</h3>
        <ol style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
          <li>The wallet calls <code>GET /api/demo-issuer/credential?subject=&lt;holder DID&gt;</code>.</li>
          <li>The demo issuer signs a VC-JWT with its server-side Ed25519 key, addressed to your subject DID.</li>
          <li>The wallet stores the JWT in <code>localStorage</code> with extracted metadata for the preview card.</li>
          <li>This is <em>conceptually</em> an OID4VCI Credential Endpoint — the wire-format-compliant flow (credential offer URI, pre-authorised code, token endpoint) is deferred to Phase 1.1.</li>
        </ol>
      </div>
    </>
  )
}
