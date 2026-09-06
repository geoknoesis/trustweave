'use client'

import { useRef, useState } from 'react'
import { exportWalletData } from '@/lib/storage'
import { restoreCredentials } from '@/lib/wallet'

export function CredentialBackup({ onRestored }: { onRestored: () => Promise<void> }) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const download = () => {
    try {
      setError(null)
      const url = URL.createObjectURL(new Blob([exportWalletData()], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url; link.download = 'trustweave-wallet-recovery.json'; link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch { setError('Could not export credentials. Check browser storage permissions.') }
  }
  const restore = async (file: File) => {
    setError(null); setMessage(null)
    if (file.size > 5 * 1024 * 1024) { setError('Choose a backup smaller than 5 MB.'); return }
    if (!window.confirm('Restore credentials to this wallet? Existing credentials and your identity will be kept.')) return
    setBusy(true)
    try {
      const result = await restoreCredentials(await file.text())
      setMessage(`Restored ${result.added} credential${result.added === 1 ? '' : 's'}; ${result.skipped} already present.`)
      await onRestored()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Restore failed. Your existing credentials were preserved.') }
    finally { setBusy(false) }
  }
  return <details className="identity-section">
    <summary>Back up and restore credentials</summary>
    <p>Keep this file private: it contains your credential details. It does not contain signing keys. Restore works only with the same wallet identity and its existing device key.</p>
    <button type="button" className="btn secondary" onClick={download} disabled={busy}>Export credentials</button>
    <button type="button" className="btn secondary" onClick={() => input.current?.click()} disabled={busy}>{busy ? 'Restoring…' : 'Restore credentials'}</button>
    <input ref={input} type="file" accept="application/json,.json" aria-label="Credential backup file" hidden onChange={event => {
      const file = event.target.files?.[0]; event.target.value = ''; if (file) void restore(file)
    }} />
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
  </details>
}
