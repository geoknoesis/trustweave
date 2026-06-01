/**
 * FAA demonstration registry — issues DroneIdentificationCredentials with
 * registered aircraft metadata and tamper-evident photo digests.
 */
import 'server-only'
import fs from 'fs'
import path from 'path'
import { sha256 } from '@noble/hashes/sha256'
import { b64uEncode } from '../crypto'

const DOMAIN_DIR = path.join(process.cwd(), 'data/trust-domains/demo-faa-drone-registry')
const PUBLIC_DIR = path.join(process.cwd(), 'public')

export interface FaaTrustDomainInfo {
  domainId: string
  name: string
  description: string
  authorityName: string
  authorityContact: string
  defaultDroneId: string
}

export interface FaaDroneRecord {
  droneId: string
  registrationNumber: string
  make: string
  model: string
  serialNumber: string
  weightClass: string
  callsign: string
  photoFile: string
  vct: string
}

export interface FaaDroneSummary {
  droneId: string
  registrationNumber: string
  make: string
  model: string
  callsign: string
  vct: string
}

interface LoadedFaaTrustDomain {
  info: FaaTrustDomainInfo
  drones: FaaDroneRecord[]
  byDroneId: Map<string, FaaDroneRecord>
}

let cached: LoadedFaaTrustDomain | null = null
const photoDigestCache = new Map<string, string>()

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

function loadDronesCsv(filePath: string): FaaDroneRecord[] {
  const raw = fs.readFileSync(filePath, 'utf8')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return []

  const headers = parseCsvLine(lines[0])
  const records: FaaDroneRecord[] = []

  for (let i = 1; i < lines.length; i++) {
    const values = parseCsvLine(lines[i])
    const row: Record<string, string> = {}
    headers.forEach((h, idx) => {
      row[h] = values[idx] ?? ''
    })
    records.push({
      droneId: row.droneId,
      registrationNumber: row.registrationNumber,
      make: row.make,
      model: row.model,
      serialNumber: row.serialNumber,
      weightClass: row.weightClass,
      callsign: row.callsign,
      photoFile: row.photoFile,
      vct: row.vct,
    })
  }
  return records
}

function loadTrustDomain(): LoadedFaaTrustDomain {
  if (cached) return cached

  const infoPath = path.join(DOMAIN_DIR, 'domain.json')
  const csvPath = path.join(DOMAIN_DIR, 'drones.csv')

  if (!fs.existsSync(infoPath) || !fs.existsSync(csvPath)) {
    throw new Error(
      `FAA demo registry data missing under ${DOMAIN_DIR}. ` +
        'Ensure data/trust-domains/demo-faa-drone-registry/ is present.',
    )
  }

  const info = JSON.parse(fs.readFileSync(infoPath, 'utf8')) as FaaTrustDomainInfo
  const drones = loadDronesCsv(csvPath)
  const byDroneId = new Map(drones.map((d) => [d.droneId, d]))

  cached = { info, drones, byDroneId }
  return cached
}

/** SHA-256 digest (base64url) of the registered aircraft photo file. */
export function dronePhotoDigest(photoFile: string): string {
  const cachedDigest = photoDigestCache.get(photoFile)
  if (cachedDigest) return cachedDigest

  const photoPath = path.join(PUBLIC_DIR, photoFile)
  if (!fs.existsSync(photoPath)) {
    throw new Error(`Drone photo missing: ${photoPath}`)
  }
  const bytes = fs.readFileSync(photoPath)
  const digest = b64uEncode(sha256(bytes))
  photoDigestCache.set(photoFile, digest)
  return digest
}

export function buildDronePhotoUrl(origin: string, photoFile: string): string {
  const base = origin.replace(/\/$/, '')
  const pathPart = photoFile.startsWith('/') ? photoFile : `/${photoFile}`
  return `${base}${pathPart}`
}

export function getFaaTrustDomain(): FaaTrustDomainInfo {
  return loadTrustDomain().info
}

export function listFaaDrones(): FaaDroneRecord[] {
  return [...loadTrustDomain().drones]
}

export function listFaaDroneSummaries(): FaaDroneSummary[] {
  return listFaaDrones().map(({ droneId, registrationNumber, make, model, callsign, vct }) => ({
    droneId,
    registrationNumber,
    make,
    model,
    callsign,
    vct,
  }))
}

export function faaAcceptedCredentialTypes(): string[] {
  const types = new Set(listFaaDrones().map((d) => d.vct))
  return [...types].sort()
}

export function resolveFaaDrone(droneId?: string | null): FaaDroneRecord {
  const domain = loadTrustDomain()
  const id = droneId?.trim() || domain.info.defaultDroneId
  const record = domain.byDroneId.get(id)
  if (!record) {
    const known = [...domain.byDroneId.keys()].join(', ')
    throw new Error(`Unknown droneId "${id}". Known FAA demo drones: ${known}`)
  }
  return record
}

export function faaRecordToDisclosableClaims(
  record: FaaDroneRecord,
  domain: FaaTrustDomainInfo,
  origin: string,
) {
  const photoUrl = buildDronePhotoUrl(origin, record.photoFile)
  return [
    { name: 'trustDomainId', value: domain.domainId },
    { name: 'issuingAuthority', value: domain.authorityName },
    { name: 'make', value: record.make },
    { name: 'model', value: record.model },
    { name: 'serialNumber', value: record.serialNumber },
    { name: 'weightClass', value: record.weightClass },
    { name: 'callsign', value: record.callsign },
    { name: 'dronePhotoUrl', value: photoUrl },
    { name: 'dronePhotoDigest', value: dronePhotoDigest(record.photoFile) },
  ]
}

export function faaRecordDisclosableNames(_record: FaaDroneRecord): string[] {
  return [
    'trustDomainId',
    'issuingAuthority',
    'make',
    'model',
    'serialNumber',
    'weightClass',
    'callsign',
    'dronePhotoUrl',
    'dronePhotoDigest',
  ]
}

export function faaRecordPhotoPath(record: FaaDroneRecord): string {
  return `/${record.photoFile.replace(/^\//, '')}`
}
