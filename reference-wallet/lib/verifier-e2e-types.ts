export interface E2eTestStep {
  name: string
  ok: boolean
  detail?: string
}

export interface E2eTestResult {
  ok: boolean
  studentId: string
  holderDid: string
  vct?: string
  steps: E2eTestStep[]
  verify?: {
    valid: boolean
    checks: Array<{ step: string; passed: boolean; detail?: string }>
    credentials?: Array<{
      type: string[]
      issuer: string
      subject: string
      disclosedClaims: Record<string, unknown>
    }>
  }
}
