'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { CredentialOfferQr } from '@/components/CredentialOfferQr'
import { DroneAuthorizationPanel } from '@/components/DroneAuthorizationPanel'
import { buildCredentialOfferQrPayload } from '@/lib/credential-offer-qr'
import {
  buildAirspacePortalUrl,
  fetchDroneDetail,
  SPATIAL_CREDENTIAL_ENDPOINT,
  type DroneDetailResponse,
} from '@/lib/spatial-domain-client'

export default function IssuerDroneDetailPage() {
  const params = useParams()
  const droneId = String(params.droneId ?? '')
  const [detail, setDetail] = useState<DroneDetailResponse | null>(null)
  const [issuerUrl, setIssuerUrl] = useState('')
  const [urlHint, setUrlHint] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/demo-issuer/server-url')
      .then((r) => r.json())
      .then((data: { origin: string; mobileReachableUrl: string | null; hint: string | null }) => {
        setIssuerUrl(data.mobileReachableUrl ?? data.origin)
        if (data.hint) setUrlHint(data.hint)
      })
      .catch(() => setIssuerUrl(window.location.origin))
  }, [])

  useEffect(() => {
    if (!droneId) return
    setError(null)
    fetchDroneDetail(droneId)
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [droneId])

  const offerParams = detail
    ? { droneId: detail.drone.droneId, trustDomainId: detail.domain.domainId }
    : null

  return (
    <>
      <div className="page-hero">
        <h2>{detail?.drone.callsign ?? droneId}</h2>
        <p>
          Activity authorization from the <code>demo-sf-airspace</code> spatial trust domain.
          Scan the QR with the wallet&apos;s <Link href="/receive">Receive</Link> tab to claim this credential.
        </p>
      </div>

      <div className="breadcrumb-row">
        <Link href="/issuer/airspace">All drones</Link>
        <span aria-hidden="true">/</span>
        <span>{droneId}</span>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Drone not found:</strong> {error}
        </div>
      )}

      {detail && (
        <>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Authorization record</h3>
            <DroneAuthorizationPanel
              drone={detail.drone}
              domainName={detail.domain.name}
              selectivelyDisclosable={detail.selectivelyDisclosable}
            />
            <div className="identity-card" style={{ marginTop: '1rem' }}>
              <div className="label">Domain authority DID</div>
              <div className="identity-value">{detail.issuerDid}</div>
            </div>
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Drone wallet offer QR</h3>
            <label style={{ display: 'block', marginBottom: '1rem' }}>
              <div className="label" style={{ marginBottom: '0.35rem' }}>Issuer URL in QR (must reach phone on LAN)</div>
              <input
                value={issuerUrl}
                onChange={(e) => setIssuerUrl(e.target.value)}
                style={{
                  width: '100%',
                  padding: '0.5rem',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  fontFamily: 'monospace',
                  fontSize: '0.85rem',
                }}
              />
              {urlHint && (
                <div className="callout warning" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>{urlHint}</div>
              )}
            </label>

            {offerParams && issuerUrl && (
              <>
                <div style={{ textAlign: 'center', margin: '1rem 0' }}>
                  <CredentialOfferQr
                    issuerUrl={issuerUrl}
                    params={offerParams}
                    credentialEndpoint={SPATIAL_CREDENTIAL_ENDPOINT}
                    size={220}
                  />
                </div>
                <div className="identity-card">
                  <div className="label">Offer payload (JSON in QR)</div>
                  {buildCredentialOfferQrPayload(issuerUrl, offerParams, SPATIAL_CREDENTIAL_ENDPOINT)}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Shareable portal link</div>
                  {buildAirspacePortalUrl(issuerUrl, detail.drone.droneId, detail.domain.domainId)}
                </div>
              </>
            )}
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Next steps</h3>
            <ol className="step-list">
              <li>
                Register with the FAA: <Link href={`/issuer/faa/${detail.drone.droneId}`}>FAA identification + photo</Link>.
              </li>
              <li>Scan the offer QR from a phone wallet (Expo app or this web wallet on another device).</li>
              <li>Open <Link href="/airspace/gate">Airspace gate</Link> and set the drone&apos;s intended location.</li>
              <li>From the wallet <Link href="/present">Share</Link> tab, scan the gate QR and present the authorization.</li>
            </ol>
          </div>
        </>
      )}
    </>
  )
}
