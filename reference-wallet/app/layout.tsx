import './globals.css'
import Link from 'next/link'
import type { ReactNode } from 'react'

export const metadata = {
  title: 'TrustWeave Reference Wallet',
  description: 'Walking-skeleton holder wallet demo for the TrustWeave Wallet SDK',
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="app-header">
          <div className="container">
            <div>
              <h1>TrustWeave Reference Wallet</h1>
              <div className="subtitle">Phase 1 walking skeleton · not a product</div>
            </div>
            <nav>
              <Link href="/">Wallet</Link>
              <Link href="/receive">Receive</Link>
              <Link href="/present">Present</Link>
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
            <a href="/api/demo-issuer/credential?subject=did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH" target="_blank" rel="noreferrer">
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
