'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { CredentialOfferQr } from '@/components/CredentialOfferQr'
import { DegreeRecordPanel } from '@/components/DegreeRecordPanel'
import {
  buildCredentialOfferQrPayload,
  buildCredentialOfferUrl,
  buildDegreePortalUrl,
} from '@/lib/credential-offer-qr'
import {
  fetchDegreeDetail,
  type DegreeDetailResponse,
} from '@/lib/trust-domain-client'

export default function IssuerDegreeDetailPage() {
  const params = useParams()
  const studentId = String(params.studentId ?? '')
  const [detail, setDetail] = useState<DegreeDetailResponse | null>(null)
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
    if (!studentId) return
    setError(null)
    fetchDegreeDetail(studentId)
      .then(setDetail)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [studentId])

  const offerParams = detail
    ? { studentId: detail.degree.studentId, trustDomainId: detail.domain.domainId }
    : null

  return (
    <>
      <div className="page-hero">
        <h2>{detail?.degree.name ?? studentId}</h2>
        <p>
          Graduate degree record from the <code>demo-university</code> trust domain.
          Scan the QR with the wallet&apos;s <Link href="/receive">Receive</Link> tab to claim this credential.
        </p>
      </div>

      <div className="breadcrumb-row">
        <Link href="/issuer/degrees">All degrees</Link>
        <span aria-hidden="true">/</span>
        <span>{studentId}</span>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Degree not found:</strong> {error}
        </div>
      )}

      {detail && (
        <>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Registrar record</h3>
            <DegreeRecordPanel
              degree={detail.degree}
              selectivelyDisclosable={detail.selectivelyDisclosable}
            />
            <div className="identity-card" style={{ marginTop: '1rem' }}>
              <div className="label">Issuer DID</div>
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
                style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)', fontFamily: 'monospace', fontSize: '0.85rem' }}
              />
              {urlHint && (
                <div className="callout warning" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>{urlHint}</div>
              )}
            </label>

            {offerParams && issuerUrl && (
              <>
                <div style={{ textAlign: 'center', margin: '1rem 0' }}>
                  <CredentialOfferQr issuerUrl={issuerUrl} params={offerParams} size={220} />
                </div>
                <div className="identity-card">
                  <div className="label">Offer payload (JSON in QR)</div>
                  {buildCredentialOfferQrPayload(issuerUrl, offerParams)}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Shareable portal link</div>
                  {buildDegreePortalUrl(issuerUrl, detail.degree.studentId, detail.domain.domainId)}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Legacy offer console link</div>
                  {buildCredentialOfferUrl(issuerUrl, offerParams)}
                </div>
              </>
            )}
          </div>

          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Next steps</h3>
            <ol className="step-list">
              <li>Graduate scans the QR in the wallet app → <strong>Receive</strong>.</li>
              <li>Wallet calls the issuer with its holder <code>did:key</code> and stores the signed degree.</li>
              <li>An employer opens <Link href="/verifier">Demo verifier</Link> or runs the{' '}
                <Link href="/verifier/test">Verifier test</Link> to validate a presentation.</li>
            </ol>
          </div>
        </>
      )}

      {!detail && !error && <div className="status-text loading">Loading degree…</div>}
    </>
  )
}
