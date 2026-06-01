'use client'

import QRCode from 'react-qr-code'
import { holderQrPayload } from '@/lib/holder-qr'

interface HolderDidQrProps {
  did: string
  size?: number
}

export function HolderDidQr({ did, size = 168 }: HolderDidQrProps) {
  return (
    <div className="holder-qr">
      <div className="holder-qr-frame">
        <QRCode
          value={holderQrPayload(did)}
          size={size}
          level="M"
          bgColor="#ffffff"
          fgColor="#003399"
        />
      </div>
      <div className="holder-qr-caption">
        <div className="label">Share your identity</div>
        <div>Let a verifier scan this before you share a credential</div>
      </div>
    </div>
  )
}
