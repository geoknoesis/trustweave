'use client'

import { useEffect, useState } from 'react'
import { removeIssuer, reviewableIssuers } from '@/lib/wallet'

/**
 * Lets the user review which issuers this wallet trusts. Configured issuers are read-only; issuers
 * the user confirmed earlier can be removed (their stored credentials can then no longer be shared).
 */
export function AcceptedIssuers() {
  const [issuers, setIssuers] = useState<ReturnType<typeof reviewableIssuers>>([])
  const [error, setError] = useState<string | null>(null)
  const refresh = () => { try { setIssuers(reviewableIssuers()) } catch { setIssuers([]) } }
  useEffect(refresh, [])
  const remove = async (did: string) => {
    if (!window.confirm(`Stop trusting ${did}? Credentials from this issuer can no longer be shared.`)) return
    try { setError(null); await removeIssuer(did); refresh() } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not remove the issuer.') }
  }
  return <details className="identity-section" onToggle={refresh}>
    <summary>Trusted issuers</summary>
    <p>Credentials are only accepted from issuers listed here, from this wallet&apos;s own backend, or after you confirm an issuer.</p>
    {issuers.length === 0 && <p>You have not confirmed any issuers.</p>}
    <ul aria-label="Trusted issuers">
      {issuers.map(({ did, source }) => <li key={did}>
        <span className="identity-value">{did}</span>{' '}
        {source === 'configured'
          ? <em>(configured)</em>
          : <button type="button" className="btn secondary" onClick={() => void remove(did)}>Remove</button>}
      </li>)}
    </ul>
    {error && <p role="alert">{error}</p>}
  </details>
}
