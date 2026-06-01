'use client'

import { useEffect, useId, useRef, useState } from 'react'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentation-request-qr'
import { explainVerifierScanFailure } from '@/lib/qr-scan-feedback'
import { useHtml5QrcodeScanner } from '@/lib/use-html5-qrcode-scanner'

interface VerifierQrScannerProps {
  onScan: (request: PresentationRequestQrPayload) => void
  onError: (message: string) => void
}

export function VerifierQrScanner({ onScan, onError }: VerifierQrScannerProps) {
  const domId = useId().replace(/:/g, '')
  const readerId = `verifier-qr-reader-${domId}`
  const [paste, setPaste] = useState('')
  const handledRef = useRef(false)
  const stopRef = useRef<() => Promise<void>>(() => Promise.resolve())

  const handleRaw = (raw: string) => {
    if (handledRef.current) return
    const request = parsePresentationRequestQr(raw)
    if (!request) {
      onError(explainVerifierScanFailure(raw))
      return
    }
    handledRef.current = true
    void stopRef.current()
    onScan(request)
  }

  const { scanning, error: cameraError, start, stop } = useHtml5QrcodeScanner(readerId, handleRaw)
  stopRef.current = stop

  useEffect(() => () => {
    void stopRef.current()
  }, [])

  useEffect(() => {
    if (cameraError) onError(cameraError)
  }, [cameraError, onError])

  const startCamera = () => {
    handledRef.current = false
    start()
  }

  return (
    <div className="offer-scanner">
      <div id={readerId} className="offer-scanner-viewport" style={{ display: scanning ? 'block' : 'none' }} />
      <div className="button-row">
        {!scanning ? (
          <button type="button" className="secondary" onClick={startCamera}>
            Open camera scanner
          </button>
        ) : (
          <button type="button" className="secondary" onClick={() => void stop()}>
            Stop camera
          </button>
        )}
      </div>
      <label style={{ display: 'block', marginTop: '0.75rem' }}>
        <div className="label" style={{ marginBottom: '0.35rem' }}>Or paste verifier JSON / URL</div>
        <textarea
          value={paste}
          onChange={(e) => setPaste(e.target.value)}
          rows={3}
          placeholder='{"type":"trustweave-presentation-request","verifierUrl":"…"}'
          style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid var(--border)', fontFamily: 'monospace', fontSize: '0.8rem' }}
        />
      </label>
      <button type="button" disabled={!paste.trim()} onClick={() => handleRaw(paste)} style={{ marginTop: '0.5rem' }}>
        Use pasted request
      </button>
    </div>
  )
}
