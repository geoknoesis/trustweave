'use client'

import Link from 'next/link'

const demos = [
  {
    id: 'cac',
    title: 'DoD — Common Access Card (demo)',
    description:
      'Personnel CAC credentials with registered portrait JPEGs (480×640). ' +
      'Portrait appears in wallet details and on the verifier when disclosed.',
    steps: [
      'Claim CAC → /issuer/cac/PERSON-001',
      'View portrait in wallet library',
      'Present to verifier → /verifier',
    ],
    primaryHref: '/issuer/cac/PERSON-001',
    links: [
      { href: '/issuer/cac', label: 'Personnel roster' },
      { href: '/verifier', label: 'Verifier' },
    ],
  },
  {
    id: 'spatial',
    title: 'Spatial Web — Drone airspace',
    description:
      'FAA issues DroneIdentificationCredentials with registered aircraft photos. ' +
      'Airspace authority grants activity authorization. Gatekeeper verifies crypto + location policy.',
    steps: [
      'FAA ID + photo → /issuer/faa/DRONE-001',
      'Airspace auth → /issuer/airspace/DRONE-001',
      'Present at gate → /airspace/gate',
    ],
    primaryHref: '/issuer/faa/DRONE-001',
    links: [
      { href: '/issuer/faa', label: 'FAA registry' },
      { href: '/issuer/airspace', label: 'Airspace authority' },
      { href: '/airspace/gate', label: 'Airspace gate' },
    ],
  },
  {
    id: 'university',
    title: 'Education — Demo university',
    description:
      'CSV-backed registrar issues SD-JWT degree credentials. Holder presents selectively to a live verifier.',
    steps: [
      'Claim degree → /issuer/degree/STU-001',
      'Present to verifier → /verifier',
    ],
    primaryHref: '/issuer/degree/STU-001',
    links: [
      { href: '/issuer/degrees', label: 'Degree roster' },
      { href: '/issuer/offer', label: 'Issuer console' },
      { href: '/verifier', label: 'Verifier' },
      { href: '/verifier/test', label: 'Verifier E2E test' },
    ],
  },
]

export default function DemosPage() {
  return (
    <>
      <div className="page-hero">
        <h2>Runnable demos</h2>
        <p>
          TrustWeave reference-wallet scenarios deployed with this app. Use <Link href="/receive">Receive</Link>{' '}
          and <Link href="/present">Share</Link> on a phone or second browser tab.
        </p>
      </div>

      <div className="demo-grid">
        {demos.map((demo) => (
          <div key={demo.id} className="panel demo-card">
            <h3 style={{ marginTop: 0 }}>{demo.title}</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.92rem' }}>{demo.description}</p>
            <ol className="step-list" style={{ fontSize: '0.88rem' }}>
              {demo.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
            <div className="fab-row" style={{ marginTop: '1rem' }}>
              <Link href={demo.primaryHref} className="btn">
                Start demo
              </Link>
            </div>
            <div style={{ marginTop: '0.75rem', display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
              {demo.links.map((link) => (
                <Link key={link.href} href={link.href} className="text-link">
                  {link.label}
                </Link>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="callout info">
        <strong>Local deployment</strong>
        <div style={{ fontSize: '0.9rem', marginTop: '0.35rem' }}>
          Run <code>npm run dev -- -H 0.0.0.0 -p 3000</code> from <code>reference-wallet/</code>.
          For Expo on a phone, point <code>demoBackendBaseUrl</code> at your LAN IP.
        </div>
      </div>
    </>
  )
}
