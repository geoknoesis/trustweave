'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { CredentialOfferQr } from '@/components/CredentialOfferQr'
import { CacSubjectPanel } from '@/components/CacSubjectPanel'
import { buildCredentialOfferQrPayload } from '@/lib/credential-offer-qr'
import {
  buildCacPortalUrl,
  CAC_CREDENTIAL_ENDPOINT,
  fetchCacSubjectDetail,
  type CacSubjectDetailResponse,
} from '@/lib/cac-domain-client'

export default function IssuerCacSubjectDetailPage() {
  const params = useParams()
  const personnelId = String(params.personnelId ?? '')
  const [detail, setDetail] = useState<CacSubjectDetailResponse | null>(null)
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
    if (!personnelId) return
    setError(null)
    fetchCacSubjectDetail(personnelId)
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [personnelId])

  const offerParams = detail
    ? { personnelId: detail.subject.personnelId, trustDomainId: detail.domain.domainId }
    : null

  return (
    <>
      <div className="page-hero">
        <h2>{detail?.subject.name ?? personnelId}</h2>
        <p>
          <code>CommonAccessCardCredential</code> for{' '}
          <strong>{detail?.subject.rank ?? 'personnel'}</strong> ({detail?.subject.branch ?? 'DoD demo'}).
          Portrait JPEG is registered with the card and shown in wallet and verifier when disclosed.
        </p>
      </div>

      <div className="breadcrumb-row">
        <Link href="/issuer/cac">CAC roster</Link>
        <span aria-hidden="true">/</span>
        <span>{personnelId}</span>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Subject not found:</strong> {error}
        </div>
      )}

      {detail && (
        <>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Personnel record</h3>
            <CacSubjectPanel
              subject={detail.subject}
              authorityName={detail.domain.authorityName}
              selectivelyDisclosable={detail.selectivelyDisclosable}
            />
            <div className="identity-card" style={{ marginTop: '1rem' }}>
              <div className="label">CAC issuer DID</div>
              <div className="identity-value">{detail.issuerDid}</div>
            </div>
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Wallet offer QR</h3>
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
                    credentialEndpoint={CAC_CREDENTIAL_ENDPOINT}
                    size={220}
                  />
                </div>
                <div className="identity-card">
                  <div className="label">Offer payload (JSON in QR)</div>
                  {buildCredentialOfferQrPayload(issuerUrl, offerParams, CAC_CREDENTIAL_ENDPOINT)}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Shareable portal link</div>
                  {buildCacPortalUrl(issuerUrl, detail.subject.personnelId, detail.domain.domainId)}
                </div>
              </>
            )}
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Verify with portrait</h3>
            <ol className="step-list">
              <li>Scan the offer QR from <Link href="/receive">Receive</Link>.</li>
              <li>Open the credential in your wallet library — portrait appears in details.</li>
              <li>
                Present to <Link href="/verifier">Verifier</Link> and include <code>portraitUrl</code> in disclosures.
              </li>
            </ol>
          </div>
        </>
      )}
    </>
  )
}
