'use client'

import { useRef, useState } from 'react'
import { exportWalletData } from '@/lib/storage'
import { restoreCredentials, UntrustedIssuerError } from '@/lib/wallet'

export function CredentialBackup({ onRestored }: { onRestored: () => Promise<void> }) {
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<{ text: string; confirmed: string[]; issuerDid: string } | null>(null)
  const download = () => {
    try {
      setError(null)
      const url = URL.createObjectURL(new Blob([exportWalletData()], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url; link.download = 'trustweave-wallet-recovery.json'; link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch { setError('Could not export credentials. Check browser storage permissions.') }
  }
  const run = async (text: string, confirmed: string[]) => {
    setBusy(true); setPending(null)
    try {
      const result = await restoreCredentials(text, { confirmedIssuers: confirmed })
      setMessage(`Restored ${result.added} credential${result.added === 1 ? '' : 's'}; ${result.skipped} already present.`)
      await onRestored()
    } catch (cause) {
      // An unknown issuer is never trusted because the backup names it: ask, then retry with that DID confirmed.
      if (cause instanceof UntrustedIssuerError) setPending({ text, confirmed, issuerDid: cause.issuerDid })
      else setError(cause instanceof Error ? cause.message : 'Restore failed. Your existing credentials were preserved.')
    }
    finally { setBusy(false) }
  }
  const restore = async (file: File) => {
    setError(null); setMessage(null); setPending(null)
    if (file.size > 5 * 1024 * 1024) { setError('Choose a backup smaller than 5 MB.'); return }
    if (!window.confirm('Restore credentials to this wallet? Existing credentials and your identity will be kept.')) return
    await run(await file.text(), [])
  }
  return <details className="identity-section">
    <summary>Back up and restore credentials</summary>
    <p>Keep this file private: it contains your credential details. It does not contain signing keys. Restore works only with the same wallet identity and its existing device key.</p>
    <button type="button" className="btn secondary" onClick={download} disabled={busy}>Export credentials</button>
    <button type="button" className="btn secondary" onClick={() => input.current?.click()} disabled={busy}>{busy ? 'Restoring…' : 'Restore credentials'}</button>
    <input ref={input} type="file" accept="application/json,.json" aria-label="Credential backup file" hidden onChange={event => {
      const file = event.target.files?.[0]; event.target.value = ''; if (file) void restore(file)
    }} />
    {pending && <div role="alertdialog" aria-labelledby="restore-issuer-title" className="callout warning">
      <strong id="restore-issuer-title">Trust this issuer to restore?</strong>
      <p>The backup contains a validly signed credential from an issuer this wallet does not know. Nothing has been restored yet.</p>
      <div className="identity-value">{pending.issuerDid}</div>
      <button type="button" className="btn" onClick={() => void run(pending.text, [...pending.confirmed, pending.issuerDid])}>Trust this issuer and continue</button>
      <button type="button" className="btn secondary" onClick={() => setPending(null)}>Cancel</button>
    </div>}
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
  </details>
}
