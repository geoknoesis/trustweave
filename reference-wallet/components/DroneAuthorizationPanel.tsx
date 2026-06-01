'use client'

import type { DroneAuthorizationRecord } from '@/lib/spatial-domain-client'
import { humanClaimName } from '@/lib/credential-display'

interface Props {
  drone: DroneAuthorizationRecord
  domainName: string
  selectivelyDisclosable: string[]
}

export function DroneAuthorizationPanel({ drone, domainName, selectivelyDisclosable }: Props) {
  const rows: Array<[string, string]> = [
    ['Drone ID', drone.droneId],
    ['Callsign', drone.callsign],
    ['Operator', drone.operatorName],
    ['Activity', drone.activityType],
    ['Max altitude (ft)', drone.maxAltitudeFt],
    ['Max duration', drone.maxDuration],
    ['Airspace domain', domainName],
    ['Credential type', drone.vct],
  ]

  return (
    <div>
      <dl className="detail-grid">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {selectivelyDisclosable.length > 0 && (
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
