import './globals.css'
import Link from 'next/link'
import type { ReactNode } from 'react'

export const metadata = {
  title: 'TrustWeave Wallet',
  description: 'Store, view, and share your verified digital credentials',
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="app-header">
          <div className="container">
            <div>
              <h1>TrustWeave Wallet</h1>
              <div className="subtitle">Your digital credentials library</div>
            </div>
            <nav>
              <Link href="/">My credentials</Link>
              <Link href="/demos">Demos</Link>
              <Link href="/receive">Add</Link>
              <Link href="/present">Share</Link>
              <Link href="/verifier">Verifier</Link>
              <Link href="/airspace/gate">Airspace gate</Link>
              <Link href="/issuer/faa">FAA registry</Link>
              <Link href="/issuer/cac">CAC roster</Link>
              <Link href="/issuer/airspace">Drones</Link>
              <Link href="/issuer/degrees">Degrees</Link>
              <Link href="/issuer/offer">Issuer</Link>
            </nav>
          </div>
        </header>
        <main className="container">{children}</main>
        <footer className="app-footer">
          <div className="container">
            <a href="https://github.com/geoknoesis/trustweave" target="_blank" rel="noreferrer">
              github.com/geoknoesis/trustweave
            </a>
            {' · '}
            <Link href="/demos">demos</Link>
            {' · '}
            <a href="/api/demo-issuer/cac/trust-domain" target="_blank" rel="noreferrer">
              CAC API
            </a>
            {' · '}
            <a href="/api/demo-issuer/faa/trust-domain" target="_blank" rel="noreferrer">
              FAA registry API
            </a>
            {' · '}
            <a href="/api/demo-issuer/spatial/trust-domain" target="_blank" rel="noreferrer">
              airspace API
            </a>
            {' · '}
            <a href="/api/demo-issuer/trust-domain" target="_blank" rel="noreferrer">
              university API
            </a>
            {' · '}
            <a href="/api/demo-issuer/credential?subject=did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH&studentId=STU-001" target="_blank" rel="noreferrer">
              demo issuer
            </a>
            {' · '}
            <a href="/api/demo-verifier/request" target="_blank" rel="noreferrer">
              demo verifier
            </a>
          </div>
        </footer>
      </body>
    </html>
  )
}
