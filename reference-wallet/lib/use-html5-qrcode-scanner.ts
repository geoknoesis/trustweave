import { useCallback, useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'

/** Start Html5Qrcode after the reader element is visible (post-render). */
export function useHtml5QrcodeScanner(readerId: string, onDecode: (raw: string) => void) {
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const scannerRef = useRef<Html5Qrcode | null>(null)
  const onDecodeRef = useRef(onDecode)
  onDecodeRef.current = onDecode

  useEffect(() => {
    if (!scanning) return

    let cancelled = false
    ;(async () => {
      try {
        const scanner = new Html5Qrcode(readerId)
        if (cancelled) return
        scannerRef.current = scanner
        await scanner.start(
          { facingMode: 'environment' },
          { fps: 8, qrbox: { width: 240, height: 240 } },
          (decoded) => onDecodeRef.current(decoded),
          () => {},
        )
      } catch (e) {
        if (!cancelled) {
          setScanning(false)
          setError(e instanceof Error ? e.message : 'Camera unavailable — paste the value below')
        }
      }
    })()

    return () => {
      cancelled = true
      void scannerRef.current?.stop().catch(() => {})
      scannerRef.current = null
    }
  }, [scanning, readerId])

  const start = useCallback(() => {
    setError(null)
    setScanning(true)
  }, [])

  const stop = useCallback(async () => {
    await scannerRef.current?.stop().catch(() => {})
    scannerRef.current = null
    setScanning(false)
  }, [])

  return { scanning, error, setError, start, stop, scannerRef }
}
