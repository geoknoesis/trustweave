'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { humanClaimName } from '@/lib/credential-display'
import {
  fetchTrustDomain,
  formatDegreeOption,
  type TrustDomainDiscovery,
} from '@/lib/trust-domain-client'
import type { E2eTestResult } from '@/lib/verifier-e2e-types'

const DEFAULT_DISCLOSE = ['name', 'degree', 'major', 'institution']

export default function VerifierTestPage() {
  const [trustDomain, setTrustDomain] = useState<TrustDomainDiscovery | null>(null)
  const [studentId, setStudentId] = useState('STU-001')
  const [disclose, setDisclose] = useState<string[]>(DEFAULT_DISCLOSE)
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<E2eTestResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchTrustDomain()
      .then((td) => {
        setTrustDomain(td)
        setStudentId(td.domain.defaultStudentId)
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  const selectedDegree = trustDomain?.degrees.find((d) => d.studentId === studentId)
  const claimOptions = ['studentId', 'name', 'degree', 'major', 'institution', 'graduationDate', 'gpa']

  const toggleDisclose = (name: string) => {
    setDisclose((prev) => (prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]))
  }

  const runTest = async () => {
    setRunning(true)
    setError(null)
    setResult(null)
    try {
      const res = await fetch('/api/demo-verifier/e2e-test', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ studentId, disclose }),
      })
      const body = (await res.json()) as E2eTestResult
      setResult(body)
      if (!res.ok && !body.steps?.length) {
        setError(`Test failed (HTTP ${res.status})`)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setRunning(false)
    }
  }

  return (
    <>
      <div className="page-hero">
        <h2>Verifier test harness</h2>
        <p>
          Runs the full <strong>demo-university</strong> flow in the browser — issue a degree,
          build an SD-JWT presentation, and verify it — without a phone.
        </p>
      </div>

      <div className="breadcrumb-row">
        <Link href="/verifier">Live verifier (QR)</Link>
        <span aria-hidden="true">/</span>
        <span>Automated test</span>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Test error:</strong> {error}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Scenario</h3>
        {trustDomain && (
          <div className="callout info" style={{ marginBottom: '1rem' }}>
            Trust domain <code>{trustDomain.domain.domainId}</code>
            {' · '}
            Verifier accepts: {trustDomain.acceptedCredentialTypes.join(', ')}
          </div>
        )}

        <label style={{ display: 'block', marginBottom: '1rem' }}>
          <div className="label" style={{ marginBottom: '0.35rem' }}>Degree to issue</div>
          <select
            value={studentId}
            onChange={(e) => setStudentId(e.target.value)}
            style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)' }}
          >
            {trustDomain?.degrees.map((d) => (
              <option key={d.studentId} value={d.studentId}>
                {formatDegreeOption(d)}
              </option>
            )) ?? <option value={studentId}>{studentId}</option>}
          </select>
        </label>

        {selectedDegree && (
          <div className="identity-card" style={{ marginBottom: '1rem' }}>
            <div className="label">Credential type</div>
            <div>{selectedDegree.vct}</div>
          </div>
        )}

        <div className="label" style={{ marginBottom: '0.5rem' }}>Claims to disclose in presentation</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '1rem' }}>
          {claimOptions.map((name) => (
            <label key={name} className="checkbox-chip">
              <input
                type="checkbox"
                checked={disclose.includes(name)}
                onChange={() => toggleDisclose(name)}
              />
              {humanClaimName(name)}
            </label>
          ))}
        </div>

        <div className="button-row">
          <button type="button" onClick={runTest} disabled={running || !trustDomain}>
            {running ? 'Running test…' : 'Run automated E2E test'}
          </button>
        </div>
      </div>

      {result && (
        <>
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>Pipeline steps</h3>
            <div className={`callout ${result.ok ? 'success' : 'danger'}`}>
              <strong>{result.ok ? '✓ E2E test passed' : '✗ E2E test failed'}</strong>
              <div style={{ marginTop: '0.35rem', fontSize: '0.88rem' }}>
                Student {result.studentId}
                {result.vct ? ` · ${result.vct}` : ''}
              </div>
            </div>
            <ul className="check-list">
              {result.steps.map((s, i) => (
                <li key={i} className={s.ok ? 'pass' : 'fail'}>
                  <div className="marker">{s.ok ? '✓' : '✗'}</div>
                  <div>
                    <div>{s.name}</div>
                    {s.detail && <div className="detail">{s.detail}</div>}
                  </div>
                </li>
              ))}
            </ul>
            <div className="identity-card" style={{ marginTop: '0.75rem' }}>
              <div className="label">Ephemeral test holder DID</div>
              <div className="identity-value">{result.holderDid}</div>
            </div>
          </div>

          {result.verify && (
            <div className="panel">
              <h3 style={{ marginTop: 0 }}>Verification checks</h3>
              <ul className="check-list">
                {result.verify.checks.map((c, i) => (
                  <li key={i} className={c.passed ? 'pass' : 'fail'}>
                    <div className="marker">{c.passed ? '✓' : '✗'}</div>
                    <div>
                      <div>{c.step}</div>
                      {c.detail && <div className="detail">{c.detail}</div>}
                    </div>
                  </li>
                ))}
              </ul>
              {result.verify.credentials?.map((cred, i) => (
                <div key={i} className="disclosure-block">
                  <div className="label">Disclosed claims</div>
                  {Object.entries(cred.disclosedClaims).map(([k, v]) => (
                    <div key={k}>
                      <span className="claim-key">{humanClaimName(k)}:</span>{' '}
                      {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>What this exercises</h3>
        <ol className="step-list">
          <li>
            <code>GET /api/demo-issuer/credential</code> — issue from{' '}
            <Link href="/issuer/degrees">demo-university CSV</Link>
          </li>
          <li>
            <code>GET /api/demo-verifier/request</code> — verifier session + nonce
          </li>
          <li>Holder presentation + KB-JWT (in-memory test key)</li>
          <li>
            <code>POST /api/demo-verifier/verify</code> — same checks as the live{' '}
            <Link href="/verifier">QR verifier</Link>
          </li>
        </ol>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', marginBottom: 0 }}>
          CLI equivalent: <code>node scripts/verifier-e2e.mjs</code>
        </p>
      </div>
    </>
  )
}
