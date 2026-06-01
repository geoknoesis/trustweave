'use client'

import { credentialSummary, formatReceivedDate, humanClaimName } from '@/lib/credential-display'
import { portraitSrcFromStoredCredential } from '@/lib/portrait-display'
import type { StoredCredential } from '@/lib/storage'
import { PortraitFrame } from '@/components/PortraitFrame'

interface Props {
  cred: StoredCredential
  onClose: () => void
  onDelete: () => void
}

export function CredentialDetailPanel({ cred, onClose, onDelete }: Props) {
  const summary = credentialSummary(cred)
  const portraitSrc = portraitSrcFromStoredCredential(cred)

  return (
    <div className="detail-overlay" role="dialog" aria-modal="true" aria-labelledby="cred-detail-title">
      <div className="detail-panel">
        <div className="detail-header">
          <button type="button" className="detail-close" onClick={onClose}>Close</button>
          <h2 id="cred-detail-title" className="detail-header-title">Credential details</h2>
          <span style={{ width: 48 }} />
        </div>
        <div className="detail-body">
          <div className="detail-hero" style={{ borderLeftColor: summary.accent }}>
            <h3>{summary.title}</h3>
            {summary.subtitle && <p className="detail-subtitle">{summary.subtitle}</p>}
            <div className="detail-badge-row">
              <span className="library-badge">Authentic</span>
              <span className="detail-added">Added {formatReceivedDate(cred.receivedAt)}</span>
            </div>
          </div>

          {portraitSrc && (
            <PortraitFrame
              src={portraitSrc}
              alt={`Portrait for ${summary.title}`}
              caption="Registered portrait from credential"
            />
          )}

          <div className="detail-section">
            <div className="detail-label">Issuer</div>
            <div>{summary.issuer}</div>
          </div>

          {cred.selectivelyDisclosable.length > 0 && (
            <div className="detail-section">
              <div className="detail-label">Information you can choose to share</div>
              <ul className="detail-claims">
                {cred.selectivelyDisclosable.map((name) => (
                  <li key={name}>{humanClaimName(name)}</li>
                ))}
              </ul>
            </div>
          )}

          <button type="button" className="detail-delete" onClick={onDelete}>
            Remove from wallet
          </button>
        </div>
      </div>
    </div>
  )
}
