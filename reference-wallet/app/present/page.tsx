'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { VerifierQrScanner } from '@/components/VerifierQrScanner'
import { bootstrap, createPresentation, list, type StoredCredential, type WalletState } from '@/lib/wallet'
import { humanClaimName } from '@/lib/credential-display'
import {
  credentialMatchesRequest,
  type PresentationRequestParams,
  type PresentationRequestQrPayload,
} from '@/lib/presentation-request-qr'
import {
  fetchPresentationRequestFromQr,
  submitPresentationToVerifier,
  type VerificationResponse,
} from '@/lib/presentation-client'
import { isCredentialBoundToHolder } from '@/lib/holder-binding'

type Phase = 'scan' | 'consent' | 'done'

type Status =
  | { kind: 'idle' }
  | { kind: 'loading-request' }
  | { kind: 'building-vp' }
  | { kind: 'submitting' }
  | { kind: 'done'; response: VerificationResponse }
  | { kind: 'error'; message: string }

export default function PresentPage() {
  const [state, setState] = useState<WalletState | null>(null)
  const [credentials, setCredentials] = useState<StoredCredential[]>([])
  const [phase, setPhase] = useState<Phase>('scan')
  const [verifierQr, setVerifierQr] = useState<PresentationRequestQrPayload | null>(null)
  const [request, setRequest] = useState<PresentationRequestParams | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [disclose, setDisclose] = useState<Record<string, boolean>>({})
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [scanError, setScanError] = useState<string | null>(null)

  useEffect(() => {
    setState(bootstrap())
    setCredentials(list())
  }, [])

  useEffect(() => {
    if (!selectedId) return
    const cred = credentials.find((c) => c.id === selectedId)
    if (!cred) return
    const initial: Record<string, boolean> = {}
    for (const name of cred.selectivelyDisclosable) initial[name] = true
    setDisclose(initial)
  }, [selectedId, credentials])

  useEffect(() => {
    if (phase !== 'consent' || !state) return
    const bound = request
      ? credentials.filter(
          (c) =>
            credentialMatchesRequest(c.type, request.acceptedTypes) &&
            isCredentialBoundToHolder(c, state.holder.did),
        )
      : credentials.filter((c) => isCredentialBoundToHolder(c, state.holder.did))
    if (bound.length === 0) return
    if (!selectedId || !bound.some((c) => c.id === selectedId)) {
      setSelectedId(bound[0].id)
    }
  }, [phase, state, credentials, request, selectedId])

  const onVerifierScanned = async (qr: PresentationRequestQrPayload) => {
    setScanError(null)
    setStatus({ kind: 'loading-request' })
    try {
      if (!state) throw new Error('Wallet not ready. Go back and try again.')
      const req = await fetchPresentationRequestFromQr(qr)
      const matching = credentials.filter(
        (c) =>
          credentialMatchesRequest(c.type, req.acceptedTypes) &&
          isCredentialBoundToHolder(c, state.holder.did),
      )
      if (matching.length === 0) {
        const typeMatch = credentials.some((c) => credentialMatchesRequest(c.type, req.acceptedTypes))
        throw new Error(
          typeMatch
            ? 'Your credentials were issued to a different wallet identity. Delete them and scan the issuer QR again.'
            : 'You have no credentials that match what this verifier accepts.',
        )
      }
      setVerifierQr(qr)
      setRequest(req)
      setSelectedId(matching[0].id)
      setPhase('consent')
      setStatus({ kind: 'idle' })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const onShare = async () => {
    if (!state) {
      setStatus({ kind: 'error', message: 'Wallet not ready. Go back and try again.' })
      return
    }
    if (!verifierQr) {
      setStatus({ kind: 'error', message: 'Scan a verifier QR first.' })
      return
    }
    if (!selectedId) {
      setStatus({ kind: 'error', message: 'Select a credential to share.' })
      return
    }
    const selected = credentials.find((c) => c.id === selectedId)
    if (!selected) {
      setStatus({ kind: 'error', message: 'Selected credential is no longer in your wallet.' })
      return
    }
    if (!isCredentialBoundToHolder(selected, state.holder.did)) {
      setStatus({
        kind: 'error',
        message:
          'This credential belongs to a different wallet identity. Delete it and scan the issuer QR again.',
      })
      return
    }
    try {
      setStatus({ kind: 'building-vp' })
      const req = verifierQr.presentationRequest
        ?? await fetchPresentationRequestFromQr(verifierQr)
      setRequest(req)
      const discloseNames = Object.entries(disclose).filter(([, v]) => v).map(([k]) => k)
      const vp = await createPresentation([selectedId], req.audience, req.nonce, discloseNames)
      setStatus({ kind: 'submitting' })
      const response = await submitPresentationToVerifier(
        verifierQr,
        vp,
        selected.format,
        req.nonce,
      )
      setStatus({ kind: 'done', response })
      setPhase('done')
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const resetFlow = () => {
    setPhase('scan')
    setVerifierQr(null)
    setRequest(null)
    setSelectedId(null)
    setStatus({ kind: 'idle' })
    setScanError(null)
  }

  if (!state) {
    return (
      <div className="panel">
        <div className="status-text loading">Opening your wallet…</div>
      </div>
    )
  }

  if (credentials.length === 0) {
    return (
      <>
        <div className="page-hero">
          <h2>Share credential</h2>
          <p>Scan a verifier&apos;s QR code to share a credential securely.</p>
        </div>
        <div className="panel empty-library">
          <div className="icon">📤</div>
          <h3>Nothing to share yet</h3>
          <p>Add a credential to your library first.</p>
          <Link href="/receive" className="btn">Add credential</Link>
        </div>
      </>
    )
  }

  const matchingCreds = request
    ? credentials.filter(
        (c) =>
          credentialMatchesRequest(c.type, request.acceptedTypes) &&
          isCredentialBoundToHolder(c, state.holder.did),
      )
    : credentials.filter((c) => isCredentialBoundToHolder(c, state.holder.did))
  const selectedCred = matchingCreds.find((c) => c.id === selectedId)
  const canShare = Boolean(selectedCred && isCredentialBoundToHolder(selectedCred, state.holder.did))
  const inFlight = status.kind === 'building-vp' || status.kind === 'submitting' || status.kind === 'loading-request'

  if (phase === 'scan') {
    return (
      <>
        <div className="page-hero">
          <h2>Share credential</h2>
          <p>Scan the QR code displayed by the verifier — like Europass / EUDI wallets.</p>
        </div>
        <div className="panel">
          <div className="scan-hero">
            <div className="scan-icon">📤</div>
            <strong>Scan verifier QR</strong>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', margin: '0.35rem 0 0' }}>
              Open <Link href="/verifier">Demo verifier</Link> on a screen, then scan below.
            </p>
          </div>
          <VerifierQrScanner onScan={onVerifierScanned} onError={setScanError} />
          {scanError && <div className="callout warning">{scanError}</div>}
          {status.kind === 'loading-request' && (
            <div className="status-text loading">Reading verifier request…</div>
          )}
          {status.kind === 'error' && (
            <div className="callout danger">
              <strong>Could not connect to verifier</strong>
              <div style={{ marginTop: '0.25rem' }}>{status.message}</div>
            </div>
          )}
          <ol className="step-list">
            <li>Verifier shows a QR on their screen.</li>
            <li>You scan it here with your wallet.</li>
            <li>You review and approve what to share.</li>
          </ol>
        </div>
      </>
    )
  }

  return (
    <>
      <div className="page-hero">
        <h2>Review & share</h2>
        <p>The verifier is requesting a credential. Choose what to disclose.</p>
      </div>

      {request && (
        <div className="panel callout info">
          <strong>Verifier request</strong>
          <div style={{ fontSize: '0.88rem', marginTop: '0.35rem' }}>
            Accepts: {request.acceptedTypes.join(', ') || 'any credential'}
          </div>
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Select credential</h3>
        {matchingCreds.map((c) => (
          <CredentialLibraryCard
            key={c.id}
            cred={c}
            selected={selectedId === c.id}
            onSelect={() => setSelectedId(c.id)}
          />
        ))}

        {selectedCred && selectedCred.format === 'vc+sd-jwt' && selectedCred.selectivelyDisclosable.length > 0 && (
          <>
            <h3>Choose what to share</h3>
            <div style={{ background: 'var(--surface-alt)', borderRadius: '8px', padding: '0.5rem 1rem' }}>
              {selectedCred.selectivelyDisclosable.map((name) => (
                <div key={name} className="share-option">
                  <input
                    type="checkbox"
                    id={`share-${name}`}
                    checked={disclose[name] ?? false}
                    onChange={(e) => setDisclose({ ...disclose, [name]: e.target.checked })}
                  />
                  <label htmlFor={`share-${name}`}>{humanClaimName(name)}</label>
                </div>
              ))}
            </div>
          </>
        )}

        <div className="button-row">
          {status.kind === 'error' && (
            <div className="callout danger" style={{ marginBottom: '0.75rem' }}>
              <strong>Sharing failed</strong>
              <div style={{ marginTop: '0.25rem' }}>{status.message}</div>
            </div>
          )}
          <button onClick={onShare} disabled={!canShare || inFlight}>
            {inFlight ? statusLabel(status) : 'Share with verifier'}
          </button>
          <button type="button" className="secondary" onClick={resetFlow} disabled={inFlight}>
            Scan another QR
          </button>
        </div>

        {matchingCreds.length === 0 && request && (
          <div className="callout warning" style={{ marginTop: '0.75rem' }}>
            No credentials in your wallet match this verifier request. Delete old credentials and
            scan the issuer QR again to receive a fresh copy for this wallet.
          </div>
        )}
      </div>

      {phase === 'done' && status.kind === 'done' && (
        <VerificationResultPanel response={status.response} onDone={resetFlow} />
      )}
    </>
  )
}

function VerificationResultPanel({
  response,
  onDone,
}: {
  response: VerificationResponse
  onDone: () => void
}) {
  return (
    <div className="panel">
      <h2 style={{ marginTop: 0 }}>Verification result</h2>
      <div className={`callout ${response.valid ? 'success' : 'danger'}`}>
        <strong>{response.valid ? '✓ Credential verified successfully.' : '✗ Verification could not be completed.'}</strong>
      </div>
      <ul className="check-list">
        {response.checks.map((c, i) => (
          <li key={i} className={c.passed ? 'pass' : 'fail'}>
            <div className="marker">{c.passed ? '✓' : '✗'}</div>
            <div>
              <div>{c.step}</div>
              {c.detail && <div className="detail">{c.detail}</div>}
            </div>
          </li>
        ))}
      </ul>
      {response.credentials?.map((cred, i) => (
        <div key={i} className="disclosure-block">
          <div className="label">What was shared</div>
          {Object.entries(cred.disclosedClaims).map(([k, v]) => (
            <div key={k}>
              <span className="claim-key">{humanClaimName(k)}:</span>{' '}
              {typeof v === 'object' ? JSON.stringify(v) : String(v)}
            </div>
          ))}
        </div>
      ))}
      <button type="button" className="secondary" onClick={onDone} style={{ marginTop: '1rem' }}>
        Done
      </button>
    </div>
  )
}

function statusLabel(s: Status): string {
  switch (s.kind) {
    case 'loading-request': return 'Reading verifier request…'
    case 'building-vp': return 'Preparing your credential…'
    case 'submitting': return 'Sending to verifier…'
    default: return ''
  }
}
