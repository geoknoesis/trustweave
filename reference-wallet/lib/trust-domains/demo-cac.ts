/**
 * Demo Common Access Card (CAC) registry — personnel records with portrait JPGs.
 */
import 'server-only'
import fs from 'fs'
import path from 'path'
import { sha256 } from '@noble/hashes/sha256'
import { b64uEncode } from '../crypto'

const DOMAIN_DIR = path.join(process.cwd(), 'data/trust-domains/demo-cac')
const PUBLIC_DIR = path.join(process.cwd(), 'public')

export interface CacTrustDomainInfo {
  domainId: string
  name: string
  description: string
  authorityName: string
  authorityContact: string
  defaultPersonnelId: string
}

export interface CacSubjectRecord {
  personnelId: string
  dodId: string
  name: string
  rank: string
  branch: string
  portraitFile: string
  vct: string
}

export interface CacSubjectSummary {
  personnelId: string
  dodId: string
  name: string
  rank: string
  branch: string
  vct: string
}

interface LoadedCacTrustDomain {
  info: CacTrustDomainInfo
  subjects: CacSubjectRecord[]
  byPersonnelId: Map<string, CacSubjectRecord>
}

let cached: LoadedCacTrustDomain | null = null
const portraitDigestCache = new Map<string, string>()

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

function loadSubjectsCsv(filePath: string): CacSubjectRecord[] {
  const raw = fs.readFileSync(filePath, 'utf8')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return []

  const headers = parseCsvLine(lines[0])
  const records: CacSubjectRecord[] = []

  for (let i = 1; i < lines.length; i++) {
    const values = parseCsvLine(lines[i])
    const row: Record<string, string> = {}
    headers.forEach((h, idx) => {
      row[h] = values[idx] ?? ''
    })
    records.push({
      personnelId: row.personnelId,
      dodId: row.dodId,
      name: row.name,
      rank: row.rank,
      branch: row.branch,
      portraitFile: row.portraitFile,
      vct: row.vct,
    })
  }
  return records
}

function loadTrustDomain(): LoadedCacTrustDomain {
  if (cached) return cached

  const infoPath = path.join(DOMAIN_DIR, 'domain.json')
  const csvPath = path.join(DOMAIN_DIR, 'subjects.csv')

  if (!fs.existsSync(infoPath) || !fs.existsSync(csvPath)) {
    throw new Error(
      `CAC demo registry data missing under ${DOMAIN_DIR}. ` +
        'Ensure data/trust-domains/demo-cac/ is present.',
    )
  }

  const info = JSON.parse(fs.readFileSync(infoPath, 'utf8')) as CacTrustDomainInfo
  const subjects = loadSubjectsCsv(csvPath)
  const byPersonnelId = new Map(subjects.map((s) => [s.personnelId, s]))

  cached = { info, subjects, byPersonnelId }
  return cached
}

/** SHA-256 digest (base64url) of the portrait JPEG file. */
export function subjectPortraitDigest(portraitFile: string): string {
  const cachedDigest = portraitDigestCache.get(portraitFile)
  if (cachedDigest) return cachedDigest

  const portraitPath = path.join(PUBLIC_DIR, portraitFile)
  if (!fs.existsSync(portraitPath)) {
    throw new Error(`CAC portrait missing: ${portraitPath}`)
  }
  const bytes = fs.readFileSync(portraitPath)
  const digest = b64uEncode(sha256(bytes))
  portraitDigestCache.set(portraitFile, digest)
  return digest
}

// The portrait travels INSIDE the credential as an inline data URI, so the holder's wallet renders it
// straight from the received credential — nothing is pre-bundled in the wallet or fetched separately.
// Capped to keep credentials (and wallet storage) small; the shrunk demo portraits are ~16 KB.
const MAX_PORTRAIT_BYTES = 90_000
const portraitDataUriCache = new Map<string, string>()

/** Inline `data:image/jpeg;base64,...` of the portrait JPEG so the photo is carried by the credential. */
export function subjectPortraitDataUri(portraitFile: string): string {
  const cached = portraitDataUriCache.get(portraitFile)
  if (cached) return cached

  const portraitPath = path.join(PUBLIC_DIR, portraitFile)
  if (!fs.existsSync(portraitPath)) {
    throw new Error(`CAC portrait missing: ${portraitPath}`)
  }
  const bytes = fs.readFileSync(portraitPath)
  if (bytes.length > MAX_PORTRAIT_BYTES) {
    throw new Error(
      `CAC portrait ${portraitFile} is ${bytes.length} bytes, exceeds MAX_PORTRAIT_BYTES=${MAX_PORTRAIT_BYTES}`,
    )
  }
  const dataUri = `data:image/jpeg;base64,${bytes.toString('base64')}`
  portraitDataUriCache.set(portraitFile, dataUri)
  return dataUri
}

export function buildSubjectPortraitUrl(origin: string, portraitFile: string): string {
  const base = origin.replace(/\/$/, '')
  const pathPart = portraitFile.startsWith('/') ? portraitFile : `/${portraitFile}`
  return `${base}${pathPart}`
}

export function getCacTrustDomain(): CacTrustDomainInfo {
  return loadTrustDomain().info
}

export function listCacSubjects(): CacSubjectRecord[] {
  return [...loadTrustDomain().subjects]
}

export function listCacSubjectSummaries(): CacSubjectSummary[] {
  return listCacSubjects().map(({ personnelId, dodId, name, rank, branch, vct }) => ({
    personnelId,
    dodId,
    name,
    rank,
    branch,
    vct,
  }))
}

export function cacAcceptedCredentialTypes(): string[] {
  const types = new Set(listCacSubjects().map((s) => s.vct))
  return [...types].sort()
}

export function resolveCacSubject(personnelId?: string | null): CacSubjectRecord {
  const domain = loadTrustDomain()
  const id = personnelId?.trim() || domain.info.defaultPersonnelId
  const record = domain.byPersonnelId.get(id)
  if (!record) {
    const known = [...domain.byPersonnelId.keys()].join(', ')
    throw new Error(`Unknown personnelId "${id}". Known CAC demo subjects: ${known}`)
  }
  return record
}

export function cacRecordToDisclosableClaims(
  record: CacSubjectRecord,
  domain: CacTrustDomainInfo,
  origin: string,
) {
  const portraitUrl = buildSubjectPortraitUrl(origin, record.portraitFile)
  return [
    { name: 'trustDomainId', value: domain.domainId },
    { name: 'issuingAuthority', value: domain.authorityName },
    { name: 'name', value: record.name },
    { name: 'rank', value: record.rank },
    { name: 'branch', value: record.branch },
    { name: 'dodId', value: record.dodId },
    // Inline image carried by the credential — the wallet displays this, not a pre-stored/fetched copy.
    { name: 'portrait', value: subjectPortraitDataUri(record.portraitFile) },
    { name: 'portraitUrl', value: portraitUrl },
    { name: 'portraitDigest', value: subjectPortraitDigest(record.portraitFile) },
  ]
}

export function cacRecordDisclosableNames(_record: CacSubjectRecord): string[] {
  return [
    'trustDomainId',
    'issuingAuthority',
    'name',
    'rank',
    'branch',
    'dodId',
    'portrait',
    'portraitUrl',
    'portraitDigest',
  ]
}

export function cacRecordPortraitPath(record: CacSubjectRecord): string {
  return `/${record.portraitFile.replace(/^\//, '')}`
}
