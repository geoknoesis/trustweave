/**
 * Run the demo-university issuer → holder → verifier round-trip without a phone.
 */
import { NextRequest, NextResponse } from 'next/server'
import { runVerifierE2eTest } from '@/lib/verifier-e2e-run'

export async function POST(req: NextRequest) {
  let studentId = 'STU-001'
  let disclose = ['name', 'degree', 'major']

  try {
    const body = await req.json()
    if (typeof body.studentId === 'string' && body.studentId.trim()) {
      studentId = body.studentId.trim()
    }
    if (Array.isArray(body.disclose)) {
      disclose = body.disclose.filter((v: unknown) => typeof v === 'string')
    }
  } catch {
    // defaults are fine for empty body
  }

  const origin = req.nextUrl.origin
  const result = await runVerifierE2eTest(origin, studentId, disclose)
  return NextResponse.json(result, { status: result.ok ? 200 : 422 })
}
