'use client'

import Link from 'next/link'
import { isCredentialBoundToHolder } from '@/lib/holder-binding'
import { CredentialBackup } from '@/components/CredentialBackup'
import { WalletRecovery } from '@/components/WalletRecovery'
import { useEffect, useState } from 'react'
import { CredentialDetailPanel } from '@/components/CredentialDetailPanel'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { HolderDidQr } from '@/components/HolderDidQr'
import { bootstrap, deleteCredential, resetWallet, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'

export default function HomePage() {
  const [bootError, setBootError] = useState<string | null>(null)
  const [state, setState] = useState<WalletState | null>(null)
  const [detailCred, setDetailCred] = useState<StoredCredential | null>(null)

  useEffect(() => {
    void bootstrap().then(setState).catch(error => setBootError(String(error)))
  }, [])

  if (bootError) return <WalletRecovery message={bootError} />

  if (!state) {
    return (
      <div className="panel">
        <div className="status-text loading">Opening your wallet…</div>
      </div>
    )
  }

  const { holder, credentials } = state
  const count = credentials.length
  const reissue = credentials.filter(credential => !isCredentialBoundToHolder(credential, holder.did))

  const onDelete = async (id: string) => {
    if (!confirm('Remove this credential from your wallet?')) return
    try { await deleteCredential(id) } catch (error) { setBootError(String(error)); return }
    setDetailCred(null)
    setState({ holder, credentials: credentials.filter((c) => c.id !== id) })
  }

  const onReset = async () => {
    if (!confirm('Reset your wallet? This removes all credentials and your digital identity.')) return
    try {
      const reset = await resetWallet()
      if (!reset.keysCleared) window.alert("Wallet records were cleared, but browser key storage was unavailable. Clear this site's browser data to remove any remaining keys.")
      setDetailCred(null)
      setState(await bootstrap())
    } catch (error) { setBootError(String(error)) }
  }

  return (
    <>
      <div className="page-hero">
        <h2>My credentials</h2>
        <p>
          {count === 0
            ? 'Your credential library. Issuer signatures are checked on import; verifiers check current trust and status.'
            : `${count} credential${count === 1 ? '' : 's'} stored securely on this device.`}
        </p>
      </div>

      {reissue.length > 0 && <section className="panel" aria-label="Credential reissuance">
        <h3>{reissue.length} credential{reissue.length === 1 ? '' : 's'} need reissuance</h3>
        <p>These credentials belong to an older identity. They are preserved for reference and cannot be shared with your current key. Export them before clearing browser storage.</p>
        <ol><li>Contact each issuer through a channel you already trust and report the lost key.</li><li>Ask them to revoke or replace the old credential after verifying your identity.</li><li>Give them your current wallet identity below, then add the newly issued credential.</li></ol>
        <p>Current wallet identity: <code style={{ overflowWrap: 'anywhere' }}>{holder.did}</code></p>
        <ul>{[...new Set(reissue.map(credential => credential.issuerDid))].map(issuer => <li key={issuer} style={{ overflowWrap: 'anywhere' }}>{issuer}</li>)}</ul>
        <p>Issuers decide whether reissuance is allowed. Creating this identity does not revoke the old credentials automatically.</p>
      </section>}
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
              <CredentialLibraryCard key={c.id} cred={c} needsReissue={!isCredentialBoundToHolder(c, holder.did)} onSelect={() => setDetailCred(c)} />
            ))}
          </div>
        </>
      )}

      <CredentialBackup onRestored={async () => { setState(await bootstrap()) }} />

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
