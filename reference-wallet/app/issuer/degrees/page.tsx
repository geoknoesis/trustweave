'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import {
  fetchTrustDomain,
  formatDegreeOption,
  type TrustDomainDiscovery,
} from '@/lib/trust-domain-client'

export default function IssuerDegreesPage() {
  const [trustDomain, setTrustDomain] = useState<TrustDomainDiscovery | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchTrustDomain()
      .then(setTrustDomain)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  return (
    <>
      <div className="page-hero">
        <h2>Graduate portal — degree roster</h2>
        <p>
          Each graduate has a dedicated page with their full registrar record and a wallet offer QR.
          Data comes from the <code>demo-university</code> trust domain CSV.
        </p>
      </div>

      {trustDomain && (
        <div className="callout info">
          <strong>{trustDomain.domain.name}</strong>
          <div style={{ fontSize: '0.9rem', marginTop: '0.35rem' }}>
            Trust domain <code>{trustDomain.domain.domainId}</code>
            {' · '}
            Issuer <span className="mono-inline">{trustDomain.issuerDid.slice(0, 32)}…</span>
          </div>
        </div>
      )}

      {error && (
        <div className="callout danger">
          <strong>Failed to load trust domain:</strong> {error}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Registered degrees</h3>
        {!trustDomain && !error && <div className="status-text loading">Loading roster…</div>}
        {trustDomain && (
          <ul className="degree-roster">
            {trustDomain.degrees.map((d) => (
              <li key={d.studentId}>
                <Link href={`/issuer/degree/${d.studentId}`} className="degree-roster-link">
                  <div className="degree-roster-title">{d.name}</div>
                  <div className="degree-roster-meta">
                    {formatDegreeOption(d)}
                  </div>
                  <div className="degree-roster-cta">View degree + offer QR →</div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Also available</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: 0 }}>
          The combined issuer console lets you pick any student and tune the LAN URL for phone wallets.
        </p>
        <Link href="/issuer/offer" className="text-link">Open issuer offer console →</Link>
      </div>
    </>
  )
}
