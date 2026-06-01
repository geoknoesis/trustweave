'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { CredentialOfferQr } from '@/components/CredentialOfferQr'
import { DroneIdentificationPanel } from '@/components/DroneIdentificationPanel'
import { buildCredentialOfferQrPayload } from '@/lib/credential-offer-qr'
import {
  buildFaaPortalUrl,
  FAA_CREDENTIAL_ENDPOINT,
  fetchFaaDroneDetail,
  type FaaDroneDetailResponse,
} from '@/lib/faa-domain-client'

export default function IssuerFaaDroneDetailPage() {
  const params = useParams()
  const droneId = String(params.droneId ?? '')
  const [detail, setDetail] = useState<FaaDroneDetailResponse | null>(null)
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
    fetchFaaDroneDetail(droneId)
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [droneId])

  const offerParams = detail
    ? { droneId: detail.drone.droneId, trustDomainId: detail.domain.domainId }
    : null

  return (
    <>
      <div className="page-hero">
        <h2>{detail?.drone.registrationNumber ?? droneId}</h2>
        <p>
          FAA <code>DroneIdentificationCredential</code> for{' '}
          <strong>{detail?.drone.callsign ?? droneId}</strong>. Includes registered aircraft photo
          with integrity digest. Scan the QR from the wallet <Link href="/receive">Receive</Link> tab.
        </p>
      </div>

      <div className="breadcrumb-row">
        <Link href="/issuer/faa">FAA registry</Link>
        <span aria-hidden="true">/</span>
        <span>{droneId}</span>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Aircraft not found:</strong> {error}
        </div>
      )}

      {detail && (
        <>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Registration record</h3>
            <DroneIdentificationPanel
              drone={detail.drone}
              authorityName={detail.domain.authorityName}
              selectivelyDisclosable={detail.selectivelyDisclosable}
            />
            <div className="identity-card" style={{ marginTop: '1rem' }}>
              <div className="label">FAA issuer DID</div>
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
                    credentialEndpoint={FAA_CREDENTIAL_ENDPOINT}
                    size={220}
                  />
                </div>
                <div className="identity-card">
                  <div className="label">Offer payload (JSON in QR)</div>
                  {buildCredentialOfferQrPayload(issuerUrl, offerParams, FAA_CREDENTIAL_ENDPOINT)}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Shareable portal link</div>
                  {buildFaaPortalUrl(issuerUrl, detail.drone.droneId, detail.domain.domainId)}
                </div>
              </>
            )}
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Next steps</h3>
            <ol className="step-list">
              <li>Scan the FAA offer QR to receive identification in the drone wallet.</li>
              <li>
                Claim airspace authorization from{' '}
                <Link href={`/issuer/airspace/${detail.drone.droneId}`}>spatial authority</Link>.
              </li>
              <li>
                Present both credentials at the <Link href="/airspace/gate">airspace gate</Link> as needed.
              </li>
            </ol>
          </div>
        </>
      )}
    </>
  )
}
