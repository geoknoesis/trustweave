'use client'

import QRCode from 'react-qr-code'
import { buildCredentialOfferQrPayload } from '@/lib/credential-offer-qr'

interface CredentialOfferQrProps {
  issuerUrl: string
  params: Record<string, string>
  credentialEndpoint?: string
  size?: number
}

export function CredentialOfferQr({
  issuerUrl,
  params,
  credentialEndpoint,
  size = 200,
}: CredentialOfferQrProps) {
  const value = buildCredentialOfferQrPayload(issuerUrl, params, credentialEndpoint)
  return (
    <div className="holder-qr">
      <div className="holder-qr-frame">
        <QRCode value={value} size={size} level="M" bgColor="#ffffff" fgColor="#1e3a8a" />
      </div>
      <div className="holder-qr-caption">
        <div className="label">For wallet holders</div>
        <div>Scan with Receive → Scan credential offer</div>
      </div>
    </div>
  )
}
