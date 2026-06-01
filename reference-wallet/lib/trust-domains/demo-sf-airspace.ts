/**
 * Predefined demonstration trust domain: SF Bay controlled airspace with a CSV
 * drone roster loaded at server startup.
 *
 * Server-only — API routes import this; drone wallets discover the domain via
 * GET /api/demo-issuer/spatial/trust-domain and request credentials with
 * ?droneId=DRONE-001&subject=<holder did:key>.
 */
import 'server-only'
import fs from 'fs'
import path from 'path'

const DOMAIN_DIR = path.join(process.cwd(), 'data/trust-domains/demo-sf-airspace')

export interface AirspaceBoundary {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}

export interface SpatialTrustDomainInfo {
  domainId: string
  name: string
  description: string
  authorityContact: string
  defaultDroneId: string
  boundary: AirspaceBoundary
  allowedActivities: string[]
}

/** One row from the airspace CSV — claims embedded in the issued SD-JWT VC. */
export interface DroneAuthorizationRecord {
  droneId: string
  callsign: string
  operatorName: string
  activityType: string
  maxAltitudeFt: string
  maxDuration: string
  vct: string
}

export interface DroneAuthorizationSummary {
  droneId: string
  callsign: string
  operatorName: string
  activityType: string
  vct: string
}

interface LoadedSpatialTrustDomain {
  info: SpatialTrustDomainInfo
  drones: DroneAuthorizationRecord[]
  byDroneId: Map<string, DroneAuthorizationRecord>
}

let cached: LoadedSpatialTrustDomain | null = null

function parseCsvLine(line: string): string[] {
  const fields: string[] = []
  let current = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      inQuotes = !inQuotes
      continue
    }
    if (ch === ',' && !inQuotes) {
      fields.push(current.trim())
      current = ''
      continue
    }
    current += ch
  }
  fields.push(current.trim())
  return fields
}

function loadDronesCsv(filePath: string): DroneAuthorizationRecord[] {
  const raw = fs.readFileSync(filePath, 'utf8')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return []

  const headers = parseCsvLine(lines[0])
  const records: DroneAuthorizationRecord[] = []

  for (let i = 1; i < lines.length; i++) {
    const values = parseCsvLine(lines[i])
    const row: Record<string, string> = {}
    headers.forEach((h, idx) => {
      row[h] = values[idx] ?? ''
    })
    records.push({
      droneId: row.droneId,
      callsign: row.callsign,
      operatorName: row.operatorName,
      activityType: row.activityType,
      maxAltitudeFt: row.maxAltitudeFt,
      maxDuration: row.maxDuration,
      vct: row.vct,
    })
  }
  return records
}

function loadTrustDomain(): LoadedSpatialTrustDomain {
  if (cached) return cached

  const infoPath = path.join(DOMAIN_DIR, 'domain.json')
  const csvPath = path.join(DOMAIN_DIR, 'drones.csv')

  if (!fs.existsSync(infoPath) || !fs.existsSync(csvPath)) {
    throw new Error(
      `Demo SF airspace trust domain data missing under ${DOMAIN_DIR}. ` +
        'Ensure data/trust-domains/demo-sf-airspace/ is present.',
    )
  }

  const info = JSON.parse(fs.readFileSync(infoPath, 'utf8')) as SpatialTrustDomainInfo
  const drones = loadDronesCsv(csvPath)
  const byDroneId = new Map(drones.map((d) => [d.droneId, d]))

  cached = { info, drones, byDroneId }
  return cached
}

export function getDemoSfAirspaceTrustDomain(): SpatialTrustDomainInfo {
  return loadTrustDomain().info
}

export function listDemoSfAirspaceDrones(): DroneAuthorizationRecord[] {
  return [...loadTrustDomain().drones]
}

export function listDemoSfAirspaceDroneSummaries(): DroneAuthorizationSummary[] {
  return listDemoSfAirspaceDrones().map(({ droneId, callsign, operatorName, activityType, vct }) => ({
    droneId,
    callsign,
    operatorName,
    activityType,
    vct,
  }))
}

export function demoSfAirspaceAcceptedTypes(): string[] {
  const types = new Set(listDemoSfAirspaceDrones().map((d) => d.vct))
  return [...types].sort()
}

export function resolveDemoSfAirspaceDrone(droneId?: string | null): DroneAuthorizationRecord {
  const domain = loadTrustDomain()
  const id = droneId?.trim() || domain.info.defaultDroneId
  const record = domain.byDroneId.get(id)
  if (!record) {
    const known = [...domain.byDroneId.keys()].join(', ')
    throw new Error(`Unknown droneId "${id}". Known demo drones: ${known}`)
  }
  return record
}

export function droneRecordToDisclosableClaims(
  record: DroneAuthorizationRecord,
  domain: SpatialTrustDomainInfo,
) {
  return [
    { name: 'trustDomainId', value: domain.domainId },
    { name: 'droneId', value: record.droneId },
    { name: 'callsign', value: record.callsign },
    { name: 'operatorName', value: record.operatorName },
    { name: 'activityType', value: record.activityType },
    { name: 'domainId', value: domain.domainId },
    { name: 'maxAltitudeFt', value: record.maxAltitudeFt },
    { name: 'maxDuration', value: record.maxDuration },
  ]
}

export function droneRecordDisclosableNames(record: DroneAuthorizationRecord): string[] {
  return droneRecordToDisclosableClaims(record, getDemoSfAirspaceTrustDomain()).map((c) => c.name)
}

/** Geographic policy check after cryptographic verification. */
export function checkAirspaceAuthorization(
  domain: SpatialTrustDomainInfo,
  disclosedClaims: Record<string, unknown>,
  activityType: string,
  lat: number,
  lon: number,
): { authorized: boolean; reason?: string } {
  const claimDomainId = String(disclosedClaims.domainId ?? disclosedClaims.trustDomainId ?? '')
  if (claimDomainId !== domain.domainId) {
    return { authorized: false, reason: `Credential domain ${claimDomainId} != ${domain.domainId}` }
  }

  const claimActivity = String(disclosedClaims.activityType ?? '')
  if (claimActivity !== activityType) {
    return { authorized: false, reason: `Activity ${claimActivity} != requested ${activityType}` }
  }

  if (!domain.allowedActivities.includes(activityType)) {
    return { authorized: false, reason: `Activity ${activityType} not allowed in domain` }
  }

  const b = domain.boundary
  if (lat < b.minLat || lat > b.maxLat || lon < b.minLon || lon > b.maxLon) {
    return {
      authorized: false,
      reason: `Location (${lat}, ${lon}) outside domain boundary`,
    }
  }

  return { authorized: true }
}
