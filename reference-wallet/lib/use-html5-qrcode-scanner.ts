import { useCallback, useEffect, useRef, useState } from 'react'
import { Html5Qrcode } from 'html5-qrcode'

// The library can throw synchronously when it is already stopped or still starting.
async function stopScanner(scanner: Html5Qrcode | null) {
  try { await scanner?.stop() } catch { /* already stopped or acquisition pending */ }
}

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
    let scanner: Html5Qrcode | null = null
    ;(async () => {
      try {
        scanner = new Html5Qrcode(readerId)
        if (cancelled) return
        scannerRef.current = scanner
        await scanner.start(
          { facingMode: 'environment' },
          { fps: 8, qrbox: { width: 240, height: 240 } },
          (decoded) => { if (!cancelled) onDecodeRef.current(decoded) },
          () => {},
        )
        // stop() can reject while permission/device acquisition is still pending.
        // If the effect was cancelled in that window, release the newly acquired camera now.
        if (cancelled) await stopScanner(scanner)
      } catch (e) {
        if (!cancelled) {
          setScanning(false)
          setError(e instanceof Error ? e.message : 'Camera unavailable — paste the value below')
        }
      }
    })()

    return () => {
      cancelled = true
      void stopScanner(scanner)
      if (scannerRef.current === scanner) scannerRef.current = null
    }
  }, [scanning, readerId])

  const start = useCallback(() => {
    setError(null)
    setScanning(true)
  }, [])

  const stop = useCallback(async () => {
    await stopScanner(scannerRef.current)
    scannerRef.current = null
    setScanning(false)
  }, [])

  return { scanning, error, setError, start, stop, scannerRef }
}
