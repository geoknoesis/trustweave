/**
 * Predefined demonstration trust domain: university registrar with a CSV
 * degree roster loaded at server startup.
 *
 * Server-only — API routes import this; wallets discover the domain via
 * GET /api/demo-issuer/trust-domain and request credentials with
 * ?studentId=STU-001&subject=<holder did:key>.
 */
import 'server-only'
import fs from 'fs'
import path from 'path'

const DOMAIN_DIR = path.join(process.cwd(), 'data/trust-domains/demo-university')

export interface TrustDomainInfo {
  domainId: string
  name: string
  description: string
  country: string
  registrarContact: string
  defaultStudentId: string
}

/** One row from the registrar CSV — claims embedded in the issued SD-JWT VC. */
export interface DegreeRecord {
  studentId: string
  name: string
  degree: string
  major: string
  institution: string
  graduationDate: string
  gpa: string
  vct: string
}

/** Public summary returned to wallets (no GPA — holder receives full record on issue). */
export interface DegreeSummary {
  studentId: string
  name: string
  degree: string
  major: string
  vct: string
}

interface LoadedTrustDomain {
  info: TrustDomainInfo
  degrees: DegreeRecord[]
  byStudentId: Map<string, DegreeRecord>
}

let cached: LoadedTrustDomain | null = null

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

function loadDegreesCsv(filePath: string): DegreeRecord[] {
  const raw = fs.readFileSync(filePath, 'utf8')
  const lines = raw.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length < 2) return []

  const headers = parseCsvLine(lines[0])
  const records: DegreeRecord[] = []

  for (let i = 1; i < lines.length; i++) {
    const values = parseCsvLine(lines[i])
    const row: Record<string, string> = {}
    headers.forEach((h, idx) => {
      row[h] = values[idx] ?? ''
    })
    records.push({
      studentId: row.studentId,
      name: row.name,
      degree: row.degree,
      major: row.major,
      institution: row.institution,
      graduationDate: row.graduationDate,
      gpa: row.gpa,
      vct: row.vct,
    })
  }
  return records
}

function loadTrustDomain(): LoadedTrustDomain {
  if (cached) return cached

  const infoPath = path.join(DOMAIN_DIR, 'domain.json')
  const csvPath = path.join(DOMAIN_DIR, 'degrees.csv')

  if (!fs.existsSync(infoPath) || !fs.existsSync(csvPath)) {
    throw new Error(
      `Demo university trust domain data missing under ${DOMAIN_DIR}. ` +
        'Ensure data/trust-domains/demo-university/ is present.',
    )
  }

  const info = JSON.parse(fs.readFileSync(infoPath, 'utf8')) as TrustDomainInfo
  const degrees = loadDegreesCsv(csvPath)
  const byStudentId = new Map(degrees.map((d) => [d.studentId, d]))

  cached = { info, degrees, byStudentId }
  return cached
}

/** Metadata for the predefined demo-university trust domain. */
export function getDemoUniversityTrustDomain(): TrustDomainInfo {
  return loadTrustDomain().info
}

/** All degree rows from the preloaded CSV. */
export function listDemoUniversityDegrees(): DegreeRecord[] {
  return [...loadTrustDomain().degrees]
}

/** Summaries safe to expose in the trust-domain discovery API. */
export function listDemoUniversityDegreeSummaries(): DegreeSummary[] {
  return listDemoUniversityDegrees().map(({ studentId, name, degree, major, vct }) => ({
    studentId,
    name,
    degree,
    major,
    vct,
  }))
}

/** All distinct VCT identifiers in the CSV — used by the demo verifier. */
export function demoUniversityAcceptedTypes(): string[] {
  const types = new Set(listDemoUniversityDegrees().map((d) => d.vct))
  return [...types].sort()
}

/**
 * Look up a degree record by registrar student ID.
 * Falls back to the domain's defaultStudentId when [studentId] is omitted.
 */
export function resolveDemoUniversityDegree(studentId?: string | null): DegreeRecord {
  const domain = loadTrustDomain()
  const id = studentId?.trim() || domain.info.defaultStudentId
  const record = domain.byStudentId.get(id)
  if (!record) {
    const known = [...domain.byStudentId.keys()].join(', ')
    throw new Error(`Unknown studentId "${id}". Known demo students: ${known}`)
  }
  return record
}

/** Build selectively-disclosable claims from a CSV degree row. */
export function degreeRecordToDisclosableClaims(record: DegreeRecord) {
  return [
    { name: 'studentId', value: record.studentId },
    { name: 'name', value: record.name },
    { name: 'degree', value: record.degree },
    { name: 'major', value: record.major },
    { name: 'institution', value: record.institution },
    { name: 'graduationDate', value: record.graduationDate },
    { name: 'gpa', value: record.gpa },
  ]
}

/** Names of claims marked selectively disclosable for a degree record. */
export function degreeRecordDisclosableNames(record: DegreeRecord): string[] {
  return degreeRecordToDisclosableClaims(record).map((c) => c.name)
}
