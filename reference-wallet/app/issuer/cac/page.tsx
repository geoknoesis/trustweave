'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { formatCacSubjectOption, fetchCacTrustDomain, type CacSubjectSummary } from '@/lib/cac-domain-client'

export default function IssuerCacRosterPage() {
  const [subjects, setSubjects] = useState<CacSubjectSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchCacTrustDomain()
      .then((d) => setSubjects(d.subjects))
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  return (
    <>
      <div className="page-hero">
        <h2>Common Access Card (demo)</h2>
        <p>
          DoD-style personnel credentials with registered portrait JPEGs (480×640, EUDI-sized).
          Each card binds a subject photo via <code>portraitUrl</code> + <code>portraitDigest</code>.
        </p>
      </div>

      {error && (
        <div className="callout danger">
          <strong>Could not load roster:</strong> {error}
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Personnel roster</h3>
        {subjects.length === 0 && !error && (
          <div className="status-text loading">Loading subjects…</div>
        )}
        {subjects.length > 0 && (
          <ul className="degree-roster">
            {subjects.map((s) => (
              <li key={s.personnelId}>
                <Link href={`/issuer/cac/${s.personnelId}`} className="degree-roster-link">
                  <div className="degree-roster-title">{s.name}</div>
                  <div className="degree-roster-meta">{formatCacSubjectOption(s)}</div>
                  <div className="degree-roster-cta">View CAC credential + portrait →</div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  )
}
