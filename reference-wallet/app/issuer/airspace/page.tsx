'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import {
  fetchSpatialTrustDomain,
  formatDroneOption,
  type SpatialTrustDomainDiscovery,
} from '@/lib/spatial-domain-client'

export default function IssuerAirspacePage() {
  const [trustDomain, setTrustDomain] = useState<SpatialTrustDomainDiscovery | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchSpatialTrustDomain()
      .then(setTrustDomain)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  return (
    <>
      <div className="page-hero">
        <h2>Airspace authority — drone roster</h2>
        <p>
          Each registered drone has a dedicated authorization page with activity constraints and a
          wallet offer QR. Data comes from the <code>demo-sf-airspace</code> spatial trust domain.
        </p>
      </div>

      {trustDomain && (
        <div className="callout info">
          <strong>{trustDomain.domain.name}</strong>
          <div style={{ fontSize: '0.9rem', marginTop: '0.35rem' }}>
            Domain <code>{trustDomain.domain.domainId}</code>
            {' · '}
            Boundary lat {trustDomain.domain.boundary.minLat}–{trustDomain.domain.boundary.maxLat},
            lon {trustDomain.domain.boundary.minLon}–{trustDomain.domain.boundary.maxLon}
          </div>
        </div>
      )}

      {error && (
        <div className="callout danger">
          <strong>Failed to load spatial trust domain:</strong> {error}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Registered drones</h3>
        {!trustDomain && !error && <div className="status-text loading">Loading roster…</div>}
        {trustDomain && (
          <ul className="degree-roster">
            {trustDomain.drones.map((d) => (
              <li key={d.droneId}>
                <Link href={`/issuer/airspace/${d.droneId}`} className="degree-roster-link">
                  <div className="degree-roster-title">{d.callsign}</div>
                  <div className="degree-roster-meta">{formatDroneOption(d)}</div>
                  <div className="degree-roster-cta">View authorization + offer QR →</div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>FAA registration</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: 0 }}>
          Drones must hold an FAA <code>DroneIdentificationCredential</code> (with registered photo)
          before claiming airspace authorization.
        </p>
        <Link href="/issuer/faa" className="text-link">Open FAA registry →</Link>
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Airspace gatekeeper</h3>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: 0 }}>
          After a drone claims its authorization credential, present it at the gate before entering controlled airspace.
        </p>
        <Link href="/airspace/gate" className="text-link">Open airspace gate →</Link>
      </div>
    </>
  )
}
