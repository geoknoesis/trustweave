'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { bootstrap, deleteCredential, resetWallet, type WalletState } from '@/lib/wallet'

export default function HomePage() {
  const [state, setState] = useState<WalletState | null>(null)

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

  const { holder, credentials } = state

  const onDelete = (id: string) => {
    if (!confirm('Delete this credential? This cannot be undone.')) return
    deleteCredential(id)
    setState({ holder, credentials: state.credentials.filter((c) => c.id !== id) })
  }

  const onReset = () => {
    if (!confirm('Reset the wallet? This wipes your holder identity AND every stored credential.')) return
    resetWallet()
    setState(bootstrap())
  }

  return (
    <>
      <div className="panel">
        <h2>Your wallet</h2>
        <div className="identity-card">
          <div className="label">Holder DID</div>
          {holder.did}
        </div>
        <div className="status-text">
          Bootstrapped {new Date(holder.createdAt).toLocaleString()}. Keys live in this browser&apos;s
          localStorage (Phase 1 only; Phase 2 mobile uses Secure Enclave).
        </div>
      </div>

      <div className="panel">
        <h2>Credentials ({credentials.length})</h2>
        {credentials.length === 0 ? (
          <div className="empty">
            <div className="icon">📭</div>
            <div>No credentials yet.</div>
            <div style={{ marginTop: '1rem' }}>
              <Link href="/receive" className="btn">
                Receive a demo credential
              </Link>
            </div>
          </div>
        ) : (
          <>
            {credentials.map((c) => (
              <div key={c.id} className="credential-card">
                <div className="icon">🎓</div>
                <div className="body">
                  <div className="title">{c.preview.title}</div>
                  {c.preview.subtitle && <div className="subtitle">{c.preview.subtitle}</div>}
                  <div className="issuer">
                    issued by {c.issuerDid.slice(0, 30)}…
                  </div>
                </div>
                <button className="secondary" onClick={() => onDelete(c.id)}>
                  Delete
                </button>
              </div>
            ))}
            <div className="button-row">
              <Link href="/receive" className="btn">
                Receive another
              </Link>
              <Link href="/present" className="btn">
                Present
              </Link>
            </div>
          </>
        )}
      </div>

      <div className="panel">
        <h3>Danger zone</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: '0 0 0.75rem 0' }}>
          Wipe the wallet — irrecoverable. Use this if storage is corrupted, you want to test the
          first-run flow again, or you&apos;re done demoing.
        </p>
        <button className="danger" onClick={onReset}>
          Reset wallet
        </button>
      </div>
    </>
  )
}
