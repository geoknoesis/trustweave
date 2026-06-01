'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { CredentialDetailPanel } from '@/components/CredentialDetailPanel'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { HolderDidQr } from '@/components/HolderDidQr'
import { bootstrap, deleteCredential, resetWallet, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'

export default function HomePage() {
  const [state, setState] = useState<WalletState | null>(null)
  const [detailCred, setDetailCred] = useState<StoredCredential | null>(null)

  useEffect(() => {
    setState(bootstrap())
  }, [])

  if (!state) {
    return (
      <div className="panel">
        <div className="status-text loading">Opening your wallet…</div>
      </div>
    )
  }

  const { holder, credentials } = state
  const count = credentials.length

  const onDelete = (id: string) => {
    if (!confirm('Remove this credential from your wallet?')) return
    deleteCredential(id)
    setDetailCred(null)
    setState({ holder, credentials: credentials.filter((c) => c.id !== id) })
  }

  const onReset = () => {
    if (!confirm('Reset your wallet? This removes all credentials and your digital identity.')) return
    resetWallet()
    setDetailCred(null)
    setState(bootstrap())
  }

  return (
    <>
      <div className="page-hero">
        <h2>My credentials</h2>
        <p>
          {count === 0
            ? 'Your personal library of verified credentials.'
            : `${count} credential${count === 1 ? '' : 's'} stored securely on this device.`}
        </p>
      </div>

      {count === 0 ? (
        <div className="panel empty-library">
          <div className="icon">📚</div>
          <h3>Your library is empty</h3>
          <p>
            Add your first credential by scanning a QR code from an issuer — try the{' '}
            <Link href="/demos">Spatial Web drone demo</Link> (FAA ID + airspace gate) or{' '}
            <Link href="/issuer/degree/STU-001">demo university degrees</Link>.
          </p>
          <Link href="/receive" className="btn">
            Add credential
          </Link>
        </div>
      ) : (
        <>
          <div className="fab-row">
            <Link href="/receive" className="btn">Add credential</Link>
            <Link href="/present" className="btn secondary">Share credential</Link>
          </div>
          <div style={{ marginTop: '1rem' }}>
            {credentials.map((c) => (
              <CredentialLibraryCard key={c.id} cred={c} onSelect={() => setDetailCred(c)} />
            ))}
          </div>
        </>
      )}

      <details className="identity-section">
        <summary>Your digital identity</summary>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', margin: '0 0 0.75rem' }}>
          Show this QR when a verifier needs to identify you before you share a credential.
        </p>
        <HolderDidQr did={holder.did} />
        <div className="identity-value" style={{ marginTop: '0.75rem' }}>{holder.did}</div>
        <button type="button" className="detail-delete" style={{ marginTop: '1rem' }} onClick={onReset}>
          Reset wallet
        </button>
      </details>

      {detailCred && (
        <CredentialDetailPanel
          cred={detailCred}
          onClose={() => setDetailCred(null)}
          onDelete={() => onDelete(detailCred.id)}
        />
      )}
    </>
  )
}
