'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { bootstrap, store, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'success'; credentialId: string; type: string[]; subtitle?: string; format: StoredCredential['format']; disclosable: string[] }
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
      const body = (await response.json()) as {
        credential: string
        issuer: string
        format: StoredCredential['format']
        selectivelyDisclosable?: string[]
      }
      const stored = store(body.credential, body.format, body.selectivelyDisclosable ?? [])
      setStatus({
        kind: 'success',
        credentialId: stored.id,
        type: stored.type,
        subtitle: stored.preview.subtitle,
        format: stored.format,
        disclosable: stored.selectivelyDisclosable,
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
          The demo issuer will sign a Bachelor of Science credential addressed to your holder DID,
          as an <strong>SD-JWT VC</strong> with each personal claim marked selectively disclosable.
          Your wallet stores it locally; at presentation time you choose which claims to reveal.
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
            <div>{status.type.join(' · ')} ({status.format})</div>
            {status.subtitle && <div>{status.subtitle}</div>}
            {status.disclosable.length > 0 && (
              <div style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>
                Selectively-disclosable claims: <code>{status.disclosable.join(', ')}</code>
              </div>
            )}
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
          <li>The demo issuer builds an SD-JWT VC: an issuer JWT containing only credential metadata + an <code>_sd</code> array of disclosure hashes, plus the disclosures appended after <code>~</code>.</li>
          <li>Your wallet stores the whole thing — every disclosure — so it can choose which to reveal at presentation time.</li>
          <li>The verifier will only see the disclosures you explicitly select. Everything else stays hashed inside <code>_sd</code> and is invisible.</li>
        </ol>
      </div>
    </>
  )
}
