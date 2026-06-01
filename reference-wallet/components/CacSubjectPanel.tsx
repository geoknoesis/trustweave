import { humanClaimName } from '@/lib/credential-display'
import type { CacSubjectDetail } from '@/lib/cac-domain-client'
import { PortraitFrame } from '@/components/PortraitFrame'

interface Props {
  subject: CacSubjectDetail
  authorityName: string
  selectivelyDisclosable?: string[]
}

export function CacSubjectPanel({ subject, authorityName, selectivelyDisclosable }: Props) {
  const rows: Array<[string, string]> = [
    ['Name', subject.name],
    ['Rank', subject.rank],
    ['Branch', subject.branch],
    ['DoD ID', subject.dodId],
    ['Personnel ID', subject.personnelId],
    ['Issuing authority', authorityName],
    ['Portrait digest', `${subject.portraitDigest.slice(0, 16)}…`],
  ]

  return (
    <div>
      <PortraitFrame
        src={subject.portraitUrl}
        alt={`Portrait of ${subject.name}`}
        caption="Registered portrait (bound to credential via digest)"
      />
      <dl className="detail-grid">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {selectivelyDisclosable && selectivelyDisclosable.length > 0 && (
        <div style={{ marginTop: '1rem' }}>
          <div className="label">Selectively disclosable at presentation</div>
          <div className="tag-row">
            {selectivelyDisclosable.map((name) => (
              <span key={name} className="tag">{humanClaimName(name)}</span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
