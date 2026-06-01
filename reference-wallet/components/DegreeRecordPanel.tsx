import { humanClaimName } from '@/lib/credential-display'
import type { DegreeRecord } from '@/lib/trust-domain-client'

interface DegreeRecordPanelProps {
  degree: DegreeRecord
  selectivelyDisclosable?: string[]
  showDisclosureHint?: boolean
}

export function DegreeRecordPanel({
  degree,
  selectivelyDisclosable,
  showDisclosureHint = true,
}: DegreeRecordPanelProps) {
  const fields: Array<{ key: keyof DegreeRecord; label: string }> = [
    { key: 'studentId', label: 'Student ID' },
    { key: 'name', label: 'Graduate name' },
    { key: 'degree', label: 'Degree' },
    { key: 'major', label: 'Major' },
    { key: 'institution', label: 'Institution' },
    { key: 'graduationDate', label: 'Graduation date' },
    { key: 'gpa', label: 'GPA' },
    { key: 'vct', label: 'Credential type (VCT)' },
  ]

  return (
    <>
      <div className="degree-detail-grid">
        {fields.map(({ key, label }) => (
          <div key={key} className="degree-detail-row">
            <div className="label">{label}</div>
            <div className="degree-detail-value">{degree[key]}</div>
          </div>
        ))}
      </div>

      {showDisclosureHint && selectivelyDisclosable && selectivelyDisclosable.length > 0 && (
        <div className="callout info" style={{ marginTop: '1rem' }}>
          <strong>Selective disclosure</strong>
          <div style={{ fontSize: '0.88rem', marginTop: '0.35rem' }}>
            The holder can choose which of these claims to reveal when sharing with a verifier:
          </div>
          <div style={{ marginTop: '0.5rem', display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
            {selectivelyDisclosable.map((name) => (
              <span key={name} className="tag-chip">
                {humanClaimName(name)}
              </span>
            ))}
          </div>
        </div>
      )}
    </>
  )
}
