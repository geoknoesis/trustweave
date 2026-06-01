'use client'

import type { CSSProperties } from 'react'
import { credentialSummary } from '@/lib/credential-display'
import type { StoredCredential } from '@/lib/storage'

interface Props {
  cred: StoredCredential
  onSelect: () => void
  selected?: boolean
}

export function CredentialLibraryCard({ cred, onSelect, selected }: Props) {
  const s = credentialSummary(cred)
  return (
    <button
      type="button"
      className={`library-card${selected ? ' library-card-selected' : ''}`}
      onClick={onSelect}
      style={{ '--card-accent': s.accent } as CSSProperties}
    >
      <div className="library-card-stripe" />
      <div className="library-card-body">
        <div className="library-card-top">
          <div className="library-card-title">{s.title}</div>
          <span className="library-badge">Stored</span>
        </div>
        {s.subtitle && <div className="library-card-subtitle">{s.subtitle}</div>}
        <div className="library-card-meta">{s.issuer} · Added {s.added}</div>
      </div>
      <span className="library-card-chevron" aria-hidden>›</span>
    </button>
  )
}
