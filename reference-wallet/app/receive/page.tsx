'use client'

import Link from 'next/link'
import { WalletRecovery } from '@/components/WalletRecovery'
import { useEffect, useState } from 'react'
import { OfferQrScanner } from '@/components/OfferQrScanner'
import { bootstrap, store, UntrustedIssuerError, type WalletState } from '@/lib/wallet'
import type { IssuedCredentialResponse } from '@/lib/claim-credential'
import type { StoredCredential } from '@/lib/storage'
import { fetchCredentialFromOffer } from '@/lib/claim-credential'
import type { CredentialOfferQrPayload } from '@/lib/credential-offer-qr'
import { isCredentialBoundToHolder } from '@/lib/holder-binding'
import { credentialSummary } from '@/lib/credential-display'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'confirm-issuer'; issuerDid: string; body: IssuedCredentialResponse }
  | { kind: 'success'; credential: StoredCredential; replaced: boolean }
  | { kind: 'error'; message: string }

export default function ReceivePage() {
  const [bootError, setBootError] = useState<string | null>(null)
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [scanError, setScanError] = useState<string | null>(null)

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

  const importBody = async (body: IssuedCredentialResponse, confirmedIssuer?: string) => {
    setStatus({ kind: 'requesting' })
    try {
      const wallet = await bootstrap()
      setState(wallet)
      // The issuer named in the offer response (`body.issuer`) is deliberately NOT passed on: trust
      // comes from configuration, this wallet's own backend, or the user's explicit confirmation.
      const { credential, replaced } = await store(body.credential, body.format, body.selectivelyDisclosable ?? [], { confirmedIssuer })
      if (!isCredentialBoundToHolder(credential, wallet.holder.did)) {
        throw new Error(
          'Credential was not issued to this wallet. Refresh the page and scan the issuer QR again.',
        )
      }
      setStatus({ kind: 'success', credential, replaced })
    } catch (e) {
      if (e instanceof UntrustedIssuerError) { setStatus({ kind: 'confirm-issuer', issuerDid: e.issuerDid, body }); return }
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const claimOffer = async (offer: CredentialOfferQrPayload) => {
    setStatus({ kind: 'requesting' })
    setScanError(null)
    try {
      const wallet = await bootstrap()
      setState(wallet)
      await importBody(await fetchCredentialFromOffer(offer, wallet.holder.did))
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  return (
    <>
      <div className="page-hero">
        <h2>Add credential</h2>
        <p>Scan your issuer’s QR code. The wallet checks the issuer signature before saving the credential.</p>
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

        {status.kind === 'confirm-issuer' && (
          <div className="callout warning" role="alertdialog" aria-labelledby="confirm-issuer-title">
            <strong id="confirm-issuer-title">Trust this issuer?</strong>
            <div style={{ marginTop: '0.35rem' }}>
              This credential is signed by an issuer your wallet does not know. Only continue if you
              recognise this identifier and expected a credential from it.
            </div>
            <div className="identity-value" style={{ marginTop: '0.5rem' }}>{status.issuerDid}</div>
            <div className="button-row">
              <button type="button" className="btn" onClick={() => void importBody(status.body, status.issuerDid)}>
                Trust this issuer and add credential
              </button>
              <button type="button" className="btn secondary" onClick={() => setStatus({ kind: 'idle' })}>Cancel</button>
            </div>
          </div>
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
