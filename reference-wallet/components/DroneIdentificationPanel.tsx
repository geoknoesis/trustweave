'use client'

import type { FaaDroneRecord } from '@/lib/faa-domain-client'
import { humanClaimName } from '@/lib/credential-display'

interface Props {
  drone: FaaDroneRecord
  authorityName: string
  selectivelyDisclosable: string[]
}

export function DroneIdentificationPanel({ drone, authorityName, selectivelyDisclosable }: Props) {
  const rows: Array<[string, string]> = [
    ['Registration #', drone.registrationNumber],
    ['Drone ID', drone.droneId],
    ['Callsign', drone.callsign],
    ['Make / model', `${drone.make} ${drone.model}`],
    ['Serial number', drone.serialNumber],
    ['Weight class', drone.weightClass],
    ['Issuing authority', authorityName],
    ['Photo digest', `${drone.photoDigest.slice(0, 16)}…`],
  ]

  return (
    <div>
      <div className="drone-photo-frame" style={{ marginBottom: '1rem', textAlign: 'center' }}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={drone.photoUrl}
          alt={`Registered aircraft ${drone.callsign}`}
          style={{ maxWidth: '100%', height: 'auto', borderRadius: 8, border: '1px solid var(--border)' }}
        />
        <div className="label" style={{ marginTop: '0.5rem' }}>
          Registered aircraft photo (bound to credential via digest)
        </div>
      </div>
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
