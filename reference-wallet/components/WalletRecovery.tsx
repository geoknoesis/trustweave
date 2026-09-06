'use client'

import { exportWalletData } from '@/lib/storage'
import { resetWallet, canReplaceLostKey, replaceLostKey } from '@/lib/wallet'
import { useEffect, useState } from 'react'

/** Data remains intact on unsupported schemas, missing keys, and storage failures. */
export function WalletRecovery({ message }: { message: string }) {
  const [resetError, setResetError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [canReplace, setCanReplace] = useState(false)
  useEffect(() => { let active = true; void canReplaceLostKey().then(value => { if (active) setCanReplace(value) }).catch(() => {}); return () => { active = false } }, [])
  const replace = async () => {
    if (!window.confirm('Create a new wallet identity? Your old credentials will be kept for reissuance, but cannot be shared using the new key.')) return
    setBusy(true); setResetError(null)
    try { await replaceLostKey(); window.location.reload() }
    catch (error) { setResetError(error instanceof Error ? error.message : 'Replacement failed. Your credential data was preserved.'); setBusy(false) }
  }
  const reset = async () => {
    if (!window.confirm('Permanently delete this wallet identity and all credentials? Export your credentials first. This cannot be undone.')) return
    try {
      setBusy(true)
      setResetError(null)
      await resetWallet()
      window.location.reload()
    } catch (error) { setResetError(String(error)) }
    finally { setBusy(false) }
  }
  const exportData = () => {
    try {
      setResetError(null)
      const url = URL.createObjectURL(new Blob([exportWalletData()], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'trustweave-wallet-recovery.json'
      link.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch { setResetError('Export could not access your browser storage. Check browser storage permissions and retry. Your data has not been changed.') }
  }
  return <div className="panel" role="alert">
    <h2>Your wallet data has been preserved</h2>
    <p>{message}</p>
    <p>Export your credentials before changing browser storage. Device-bound keys cannot be exported; if a key is lost, ask the issuer to reissue the credential.</p>
    {canReplace && <button className="btn" disabled={busy} onClick={replace}>Create replacement identity</button>}
    <button className="btn" onClick={exportData}>Export credentials</button>
    <button className="btn secondary" onClick={() => window.location.reload()}>Retry</button>
    <button className="btn secondary" onClick={reset} disabled={busy}>{busy ? 'Resetting wallet…' : 'Reset wallet'}</button>
    {resetError && <p role="alert">{resetError}</p>}
  </div>
}
