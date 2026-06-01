'use client'

import QRCode from 'react-qr-code'
import {
  buildPresentationRequestQrPayload,
  type PresentationRequestParams,
} from '@/lib/presentation-request-qr'

interface VerifierRequestQrProps {
  verifierUrl?: string
  presentationRequest?: PresentationRequestParams | null
  /** Raw QR JSON — when set, overrides verifierUrl / presentationRequest builder. */
  payload?: string
  requestEndpoint?: string
  verifyEndpoint?: string
  size?: number
}

export function VerifierRequestQr({
  verifierUrl = '',
  presentationRequest,
  payload,
  requestEndpoint,
  verifyEndpoint,
  size = 200,
}: VerifierRequestQrProps) {
  const value = payload ?? (() => {
    const base = buildPresentationRequestQrPayload(
      verifierUrl,
      presentationRequest ?? undefined,
    )
    if (!requestEndpoint && !verifyEndpoint) return base
    const parsed = JSON.parse(base) as Record<string, unknown>
    if (requestEndpoint) parsed.requestEndpoint = requestEndpoint
    if (verifyEndpoint) parsed.verifyEndpoint = verifyEndpoint
    return JSON.stringify(parsed)
  })()
  return (
    <div className="holder-qr">
      <div className="holder-qr-frame">
        <QRCode value={value} size={size} level="M" bgColor="#ffffff" fgColor="#003399" />
      </div>
      <div className="holder-qr-caption">
        <div className="label">For wallet holders</div>
        <div>Scan with Share → Scan verifier QR</div>
      </div>
    </div>
  )
}
