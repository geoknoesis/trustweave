'use client'

import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useState } from 'react'
import { CredentialOfferQr } from '@/components/CredentialOfferQr'
import {
  buildCredentialOfferUrl,
  buildCredentialOfferQrPayload,
} from '@/lib/credential-offer-qr'
import {
  fetchTrustDomain,
  formatDegreeOption,
  type TrustDomainDiscovery,
} from '@/lib/trust-domain-client'

function IssuerOfferContent() {
  const searchParams = useSearchParams()
  const [trustDomain, setTrustDomain] = useState<TrustDomainDiscovery | null>(null)
  const [studentId, setStudentId] = useState('')
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
    fetchTrustDomain()
      .then((td) => {
        setTrustDomain(td)
        const fromQuery = searchParams.get('studentId')
        const initial = fromQuery && td.degrees.some((d) => d.studentId === fromQuery)
          ? fromQuery
          : td.domain.defaultStudentId
        setStudentId(initial)
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [searchParams])

  const degree = trustDomain?.degrees.find((d) => d.studentId === studentId)

  return (
    <>
      <div className="panel">
        <h2>Issuer — degree offer QR</h2>
        <p style={{ color: 'var(--text-muted)' }}>
          Combined issuer console for the <code>demo-university</code> trust domain.
          For a full graduate record, open the{' '}
          <Link href="/issuer/degrees">degree roster</Link> or a per-student{' '}
          <Link href={`/issuer/degree/${studentId || 'STU-001'}`}>detail page</Link>.
        </p>

        {trustDomain && (
          <div className="callout info">
            <strong>{trustDomain.domain.name}</strong>
            <div style={{ fontSize: '0.9rem', marginTop: '0.35rem' }}>
              Trust domain <code>{trustDomain.domain.domainId}</code>
            </div>
          </div>
        )}

        {error && (
          <div className="callout danger">
            <strong>Failed to load trust domain:</strong> {error}
          </div>
        )}

        {trustDomain && (
          <>
            <label style={{ display: 'block', marginBottom: '1rem' }}>
              <div className="label" style={{ marginBottom: '0.35rem' }}>Degree to offer</div>
              <select
                value={studentId}
                onChange={(e) => setStudentId(e.target.value)}
                style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)' }}
              >
                {trustDomain.degrees.map((d) => (
                  <option key={d.studentId} value={d.studentId}>
                    {formatDegreeOption(d)}
                  </option>
                ))}
              </select>
            </label>

            {degree && (
              <div style={{ marginBottom: '1rem' }}>
                <Link href={`/issuer/degree/${degree.studentId}`} className="text-link">
                  Open {degree.name}&apos;s degree detail page →
                </Link>
              </div>
            )}

            <label style={{ display: 'block', marginBottom: '1rem' }}>
              <div className="label" style={{ marginBottom: '0.35rem' }}>Issuer URL in QR (must reach wallet on phone)</div>
              <input
                value={issuerUrl}
                onChange={(e) => setIssuerUrl(e.target.value)}
                style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)', fontFamily: 'monospace', fontSize: '0.85rem' }}
              />
              {urlHint && (
                <div className="callout warning" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>
                  {urlHint}
                </div>
              )}
              <div style={{ color: 'var(--text-muted)', fontSize: '0.82rem', marginTop: '0.35rem' }}>
                Auto-detected from your network. Must be <code>http://&lt;PC-LAN-IP&gt;:3000</code>, never
                localhost, for phone wallets.
              </div>
            </label>

            {degree && issuerUrl && (
              <>
                <div style={{ textAlign: 'center', margin: '1rem 0' }}>
                  <CredentialOfferQr
                    issuerUrl={issuerUrl}
                    params={{
                      studentId,
                      trustDomainId: trustDomain.domain.domainId,
                    }}
                  />
                </div>
                <div className="identity-card">
                  <div className="label">Offer payload (JSON in QR)</div>
                  {buildCredentialOfferQrPayload(issuerUrl, {
                    studentId,
                    trustDomainId: trustDomain.domain.domainId,
                  })}
                </div>
                <div className="identity-card" style={{ marginTop: '0.75rem' }}>
                  <div className="label">Alternative offer URL</div>
                  {buildCredentialOfferUrl(issuerUrl, {
                    studentId,
                    trustDomainId: trustDomain.domain.domainId,
                  })}
                </div>
              </>
            )}
          </>
        )}
      </div>

      <div className="panel">
        <h3>Holder instructions</h3>
        <ol style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
          <li>Open the wallet app → <strong>Receive</strong> tab.</li>
          <li>Tap <strong>Scan credential offer</strong> and point at this code (or paste the JSON).</li>
          <li>The wallet sends its holder <code>did:key</code> to the issuer and stores the signed degree.</li>
        </ol>
      </div>
    </>
  )
}

export default function IssuerOfferPage() {
  return (
    <Suspense fallback={<div className="status-text loading">Loading issuer…</div>}>
      <IssuerOfferContent />
    </Suspense>
  )
}
