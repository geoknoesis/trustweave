'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import {
  fetchFaaTrustDomain,
  formatFaaDroneOption,
  type FaaTrustDomainDiscovery,
} from '@/lib/faa-domain-client'

export default function IssuerFaaPage() {
  const [trustDomain, setTrustDomain] = useState<FaaTrustDomainDiscovery | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchFaaTrustDomain()
      .then(setTrustDomain)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  return (
    <>
      <div className="page-hero">
        <h2>FAA UAS registration</h2>
        <p>
          The <strong>Federal Aviation Administration</strong> issues{' '}
          <code>DroneIdentificationCredential</code>s with registered aircraft metadata and
          tamper-evident photos. Pair with airspace authorization before flight.
        </p>
      </div>

      {trustDomain && (
        <div className="callout info">
          <strong>{trustDomain.domain.authorityName}</strong>
          <div style={{ fontSize: '0.9rem', marginTop: '0.35rem' }}>
            Trust domain <code>{trustDomain.domain.domainId}</code>
            {' · '}
            FAA issuer <span className="mono-inline">{trustDomain.issuerDid.slice(0, 32)}…</span>
          </div>
        </div>
      )}

      {error && (
        <div className="callout danger">
          <strong>Failed to load FAA registry:</strong> {error}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Registered aircraft</h3>
        {!trustDomain && !error && <div className="status-text loading">Loading registry…</div>}
        {trustDomain && (
          <ul className="degree-roster">
            {trustDomain.drones.map((d) => (
              <li key={d.droneId}>
                <Link href={`/issuer/faa/${d.droneId}`} className="degree-roster-link">
                  <div className="degree-roster-title">{d.registrationNumber}</div>
                  <div className="degree-roster-meta">{formatFaaDroneOption(d)}</div>
                  <div className="degree-roster-cta">View ID credential + photo →</div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Related</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: 0 }}>
          After receiving FAA identification, claim airspace activity authorization from the spatial domain authority.
        </p>
        <Link href="/issuer/airspace" className="text-link">Open airspace authority →</Link>
      </div>
    </>
  )
}
