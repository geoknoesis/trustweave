'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { OfferQrScanner } from '@/components/OfferQrScanner'
import { bootstrap, store, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { fetchCredentialFromOffer } from '@/lib/claim-credential'
import type { CredentialOfferQrPayload } from '@/lib/credential-offer-qr'
import { isCredentialBoundToHolder } from '@/lib/holder-binding'
import { credentialSummary } from '@/lib/credential-display'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'success'; credential: StoredCredential; replaced: boolean }
  | { kind: 'error'; message: string }

export default function ReceivePage() {
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [scanError, setScanError] = useState<string | null>(null)

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

  const claimOffer = async (offer: CredentialOfferQrPayload) => {
    setStatus({ kind: 'requesting' })
    setScanError(null)
    try {
      const wallet = bootstrap()
      setState(wallet)
      const body = await fetchCredentialFromOffer(offer, wallet.holder.did)
      const { credential, replaced } = store(
        body.credential,
        body.format,
        body.selectivelyDisclosable ?? [],
      )
      if (!isCredentialBoundToHolder(credential, wallet.holder.did)) {
        throw new Error(
          'Credential was not issued to this wallet. Refresh the page and scan the issuer QR again.',
        )
      }
      setStatus({ kind: 'success', credential, replaced })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  return (
    <>
      <div className="page-hero">
        <h2>Add credential</h2>
        <p>Scan the QR code from your issuer to receive a verified credential.</p>
      </div>

      <div className="panel">
        <div className="scan-hero">
          <div className="scan-icon">📷</div>
          <strong>Scan issuer QR code</strong>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: '0.35rem 0 0' }}>
            Point your camera at the credential offer shown by the organisation that issued it.
          </p>
        </div>

        <OfferQrScanner onScan={claimOffer} onError={(message) => setScanError(message)} />
        {scanError && (
          <div className="callout warning" style={{ marginTop: '0.75rem' }}>{scanError}</div>
        )}

        <ol className="step-list">
          <li>Open the offer QR from your school, employer, or issuer.</li>
          <li>Scan it here — your wallet sends a secure identity reference.</li>
          <li>The signed credential is stored in your library.</li>
        </ol>

        {status.kind === 'requesting' && (
          <div className="status-text loading">Receiving credential…</div>
        )}

        {status.kind === 'success' && (
          <div className="callout success">
            <strong>{status.replaced ? 'Credential updated in your library.' : 'Credential added to your library.'}</strong>
            <div style={{ marginTop: '0.35rem' }}>
              {credentialSummary(status.credential).title}
              {status.credential.preview.subtitle && ` — ${status.credential.preview.subtitle}`}
            </div>
            <div className="button-row">
              <Link href="/" className="btn">View library</Link>
              <Link href="/present" className="btn secondary">Share now</Link>
            </div>
          </div>
        )}

        {status.kind === 'error' && (
          <div className="callout danger">
            <strong>Could not add credential</strong>
            <div style={{ marginTop: '0.25rem' }}>{status.message}</div>
          </div>
        )}
      </div>
    </>
  )
}
